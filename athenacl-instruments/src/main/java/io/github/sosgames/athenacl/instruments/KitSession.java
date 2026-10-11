// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import io.github.sosgames.athenacl.render.GervillStreams;
import io.github.sosgames.athenacl.render.SlotSpec;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCache;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCatalog;
import io.github.sosgames.athenacl.render.soundbank.SoundbankInstaller;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankSniff;

import javax.sound.midi.Instrument;
import javax.sound.midi.InvalidMidiDataException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a kit against the soundbank cache. Missing or disallowed sources use the instrument's
 * fallback list. SF3 is rejected before Gervill sees the file. Slots are assigned by sorted name
 * so two runs of the same kit get the same patches.
 */
public final class KitSession {
    private final Kit kit;
    private final Path cache;
    private final LicensePolicy policy;
    private final Map<String, Playable> byName;
    private final int collisions;
    private final int fallbacksTaken;
    private final List<String> warnings;

    private KitSession(Kit kit, Path cache, LicensePolicy policy, Map<String, Playable> byName,
                       int collisions, int fallbacksTaken, List<String> warnings) {
        this.kit = kit;
        this.cache = cache;
        this.policy = policy;
        this.byName = byName;
        this.collisions = collisions;
        this.fallbacksTaken = fallbacksTaken;
        this.warnings = warnings;
    }

    public static KitSession open(Path file, Path cache, boolean allowUnlicensed)
            throws IOException, KitException {
        return open(Kits.load(file), cache, LicensePolicy.load(allowUnlicensed), new HashSet<>());
    }

    public Kit kit() {
        return kit;
    }

    public int collisions() {
        return collisions;
    }

    public int fallbacksTaken() {
        return fallbacksTaken;
    }

    public int sourceCount() {
        Set<String> keys = new HashSet<>();
        for (Playable playable : byName.values()) {
            keys.add(playable.sourceKey());
        }
        return keys.size();
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<Playable> instruments() {
        return List.copyOf(byName.values());
    }

    public Playable get(String name) {
        return byName.get(name);
    }

    /** {@code piano}, {@code gm-lite:piano}, or {@code gm:46}. */
    public Playable resolve(String ref) throws IOException, KitException {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        int colon = ref.indexOf(':');
        if (colon < 0) {
            return byName.get(ref);
        }
        String kitId = ref.substring(0, colon);
        String name = ref.substring(colon + 1);
        if (kitId.equals(kit.id())) {
            return byName.get(name);
        }
        if (kitId.equals("gm")) {
            try {
                int program = Integer.parseInt(name);
                Playable donor = byName.values().stream().filter(item -> !item.percussion()).findFirst().orElse(null);
                if (donor == null) {
                    return null;
                }
                return copy(donor, "gm:" + program, donor.sourceKey(), donor.file(), 0, program, null, -1, false,
                        List.of());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        Path other = Kits.find(kitId);
        if (!Files.isRegularFile(other)) {
            return null;
        }
        KitSession session = open(Kits.load(other), cache, policy, new HashSet<>());
        Playable found = session.get(name);
        return found == null ? null : copy(found, found.name(), found.sourceKey(), found.file(),
                found.sourceBank(), found.sourceProgram(), found.preset(), found.drumkit(), found.percussion(),
                List.of(ref));
    }

    private static KitSession open(Kit kit, Path cache, LicensePolicy policy, Set<String> stack)
            throws IOException, KitException {
        if (!stack.add(kit.id())) {
            throw new KitException(new KitException.Issue(
                    "KIT_EXTENDS_CYCLE", "/extends", "kit extends itself", "Remove the cycle in extends."));
        }
        Map<String, SourceState> sources = new LinkedHashMap<>();
        for (Kit.Source source : kit.sources()) {
            sources.put(source.key(), inspect(source, cache, policy));
        }
        List<String> warnings = new ArrayList<>();
        List<Bound> bound = new ArrayList<>();
        int fallbacks = 0;
        for (Kit.Instrument instrument : kit.instruments()) {
            SourceState state = sources.get(instrument.source());
            if (state == null) {
                throw new KitException(new KitException.Issue(
                        "KIT_UNKNOWN_SOURCE", "/instruments/" + instrument.name() + "/source",
                        "unknown source " + instrument.source(),
                        "Name a source from the sources list."));
            }
            if (state.sf3()) {
                throw state.error();
            }
            warnings.addAll(state.warnings());
            Playable chosen = null;
            boolean fellBack = false;
            if (state.ok() && present(state, instrument)) {
                chosen = from(kit.id(), state, instrument, List.of());
            }
            if (chosen == null) {
                for (String fallback : instrument.fallbacks()) {
                    Playable resolved = fallback(kit, cache, policy, stack, fallback);
                    if (resolved != null) {
                        chosen = copy(resolved, instrument.name(), resolved.sourceKey(), resolved.file(),
                                resolved.sourceBank(), resolved.sourceProgram(), resolved.preset(),
                                resolved.drumkit(), resolved.percussion(), List.of(fallback));
                        chosen = withChildTrim(chosen, instrument);
                        fellBack = true;
                        break;
                    }
                }
            }
            if (chosen == null) {
                throw state.error() != null ? state.error() : new KitException(new KitException.Issue(
                        "KIT_INSTRUMENT_MISSING", "/instruments/" + instrument.name(),
                        "instrument " + instrument.name() + " was not in the soundfont",
                        "Check bank, program, preset, or drumkit, or add a fallback."));
            }
            if (fellBack) {
                fallbacks++;
            }
            bound.add(new Bound(instrument.name(), chosen));
        }
        bound.sort(Comparator.comparing(Bound::name));
        Map<String, Playable> slotted = assign(bound);
        int collisions = collisions(slotted);
        return new KitSession(kit, cache, policy, slotted, collisions, fallbacks, List.copyOf(warnings));
    }

    private static Playable fallback(Kit kit, Path cache, LicensePolicy policy, Set<String> stack, String ref)
            throws IOException, KitException {
        int colon = ref.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String kitId = ref.substring(0, colon);
        String name = ref.substring(colon + 1);
        if (kitId.equals(kit.id())) {
            return null;
        }
        Path file = Kits.find(kitId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Kit loaded = Kits.load(file);
        KitSession session = open(loaded, cache, policy, stack);
        return session.get(name);
    }

    private static boolean present(SourceState state, Kit.Instrument instrument) throws IOException {
        SlotSpec spec = new SlotSpec(state.file(), instrument.bank(), Math.max(0, instrument.program()),
                instrument.preset(), instrument.drumkit(), 0, 0, instrument.percussion(), 0);
        try {
            Instrument found = GervillStreams.find(SoundbankCache.get(state.file()), spec);
            return found != null;
        } catch (InvalidMidiDataException e) {
            throw new IOException(e);
        }
    }

    private static SourceState inspect(Kit.Source source, Path cache, LicensePolicy policy) throws IOException {
        List<String> warnings = new ArrayList<>();
        if ("sf3".equalsIgnoreCase(source.format())
                || (source.path() != null && source.path().getFileName().toString().toLowerCase().endsWith(".sf3"))) {
            return SourceState.bad(sf3(source.key()), true, warnings);
        }
        LicensePolicy.Verdict verdict = policy.judge(source.license());
        if (verdict == LicensePolicy.Verdict.DENY) {
            return SourceState.bad(new KitException(new KitException.Issue(
                    "KIT_LICENSE_DENIED", "/sources/" + source.key(),
                    "license is not allowed",
                    "Use an allowed license, or pass --allow-unlicensed for a local file that must not be shipped.")),
                    false, warnings);
        }
        if (verdict == LicensePolicy.Verdict.WARN) {
            warnings.add("license " + source.license().spdx() + " needs review");
        }
        Path file = locate(source, cache);
        if (file == null || !Files.isRegularFile(file)) {
            return SourceState.bad(new KitException(new KitException.Issue(
                    "KIT_SOURCE_MISSING", "/sources/" + source.key(),
                    "soundfont for " + source.key() + " is not installed",
                    "Run soundbank install for that source, or pass --cache.")), false, warnings);
        }
        SoundbankSniff.Kind kind = SoundbankSniff.inspect(file);
        if (kind == SoundbankSniff.Kind.SF3) {
            return SourceState.bad(sf3(source.key()), true, warnings);
        }
        if (kind == SoundbankSniff.Kind.UNREADABLE) {
            return SourceState.bad(new KitException(new KitException.Issue(
                    "SOUNDBANK_UNREADABLE", "/sources/" + source.key(),
                    "soundfont could not be read",
                    "Pass an SF2 or DLS file.")), false, warnings);
        }
        if (source.sha256() != null && !source.sha256().isBlank()) {
            String hash = SoundbankInstaller.sha256(file);
            if (!hash.equalsIgnoreCase(source.sha256())) {
                return SourceState.bad(new KitException(new KitException.Issue(
                        "KIT_CHECKSUM", "/sources/" + source.key(),
                        "checksum does not match",
                        "Reinstall the soundfont. The kit expected " + source.sha256() + ".")), false, warnings);
            }
        }
        return SourceState.ok(source, file, warnings);
    }

    private static Path locate(Kit.Source source, Path cache) {
        if (source.path() != null) {
            return source.path();
        }
        if (source.catalogId() == null) {
            return null;
        }
        SoundbankCatalog.Entry entry = SoundbankCatalog.byId(source.catalogId());
        if (entry == null) {
            return null;
        }
        return SoundbankPaths.cache(cache).resolve(entry.fileName());
    }

    private static KitException sf3(String key) {
        return new KitException(new KitException.Issue(
                "SOUNDBANK_SF3_UNSUPPORTED", "/sources/" + key,
                "SF3 soundfonts are not supported",
                "use the SF2 edition or convert (e.g. Polyphone)"));
    }

    private static Playable from(String kitId, SourceState state, Kit.Instrument instrument, List<String> fallbacks) {
        return new Playable(instrument.name(), kitId, state.source().key(), state.file(),
                instrument.bank(), instrument.program(), instrument.preset(), instrument.drumkit(),
                instrument.percussion(), 0, 0, instrument.gainDb(), instrument.transpose(),
                instrument.keyLow(), instrument.keyHigh(), instrument.outOfRange(),
                state.source().license().spdx(), state.source().license().attribution(),
                state.source().license().file(), fallbacks);
    }

    private static Playable withChildTrim(Playable playable, Kit.Instrument instrument) {
        return new Playable(playable.name(), playable.kitId(), playable.sourceKey(), playable.file(),
                playable.sourceBank(), playable.sourceProgram(), playable.preset(), playable.drumkit(),
                playable.percussion(), playable.slotBank(), playable.slotProgram(),
                instrument.gainDb() != 0 ? instrument.gainDb() : playable.gainDb(),
                instrument.transpose() != 0 ? instrument.transpose() : playable.transpose(),
                playable.keyLow(), playable.keyHigh(), playable.outOfRange(),
                playable.license(), playable.attribution(), playable.licenseFile(), playable.fallbacksTaken());
    }

    private static Playable copy(Playable source, String name, String sourceKey, Path file,
                                 int bank, int program, String preset, int drumkit, boolean percussion,
                                 List<String> fallbacks) {
        return new Playable(name, source.kitId(), sourceKey, file, bank, program, preset, drumkit, percussion,
                0, 0, source.gainDb(), source.transpose(), source.keyLow(), source.keyHigh(), source.outOfRange(),
                source.license(), source.attribution(), source.licenseFile(), fallbacks);
    }

    private static Map<String, Playable> assign(List<Bound> bound) {
        List<Bound> melodic = new ArrayList<>();
        List<Bound> drums = new ArrayList<>();
        for (Bound item : bound) {
            if (item.playable().percussion()) {
                drums.add(item);
            } else {
                melodic.add(item);
            }
        }
        Map<String, Playable> slotted = new LinkedHashMap<>();
        for (int i = 0; i < melodic.size(); i++) {
            Bound item = melodic.get(i);
            slotted.put(item.name(), slot(item.playable(), 1 + i / 128, i % 128));
        }
        for (int i = 0; i < drums.size(); i++) {
            Bound item = drums.get(i);
            slotted.put(item.name(), slot(item.playable(), 0, i));
        }
        return slotted;
    }

    private static Playable slot(Playable playable, int bank, int program) {
        return new Playable(playable.name(), playable.kitId(), playable.sourceKey(), playable.file(),
                playable.sourceBank(), playable.sourceProgram(), playable.preset(), playable.drumkit(),
                playable.percussion(), bank, program, playable.gainDb(), playable.transpose(),
                playable.keyLow(), playable.keyHigh(), playable.outOfRange(),
                playable.license(), playable.attribution(), playable.licenseFile(), playable.fallbacksTaken());
    }

    private static int collisions(Map<String, Playable> slotted) {
        Set<String> seen = new HashSet<>();
        int collisions = 0;
        for (Playable playable : slotted.values()) {
            String key = playable.percussion() + ":" + playable.slotBank() + ":" + playable.slotProgram();
            if (!seen.add(key)) {
                collisions++;
            }
        }
        return collisions;
    }

    private record Bound(String name, Playable playable) {
    }

    private record SourceState(Kit.Source source, Path file, KitException error, boolean sf3, List<String> warnings) {
        static SourceState ok(Kit.Source source, Path file, List<String> warnings) {
            return new SourceState(source, file, null, false, warnings);
        }

        static SourceState bad(KitException error, boolean sf3, List<String> warnings) {
            return new SourceState(null, null, error, sf3, warnings);
        }

        boolean ok() {
            return error == null && file != null;
        }
    }
}
