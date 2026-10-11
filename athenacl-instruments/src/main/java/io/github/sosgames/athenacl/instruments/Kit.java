// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import java.nio.file.Path;
import java.util.List;

/**
 * A reusable set of named instruments drawn from one or more soundfonts.
 * {@code instruments/gm-lite.yaml} is the default. A kit may extend another and override names.
 */
public record Kit(String id, List<Source> sources, List<Instrument> instruments) {
    public Kit {
        sources = List.copyOf(sources);
        instruments = List.copyOf(instruments);
    }

    public Source source(String key) {
        for (Source source : sources) {
            if (source.key().equals(key)) {
                return source;
            }
        }
        return null;
    }

    public Instrument instrument(String name) {
        for (Instrument instrument : instruments) {
            if (instrument.name().equals(name)) {
                return instrument;
            }
        }
        return null;
    }

    public record Source(
            String key,
            String catalogId,
            Path path,
            String format,
            String sha256,
            License license) {
    }

    public record License(
            String spdx,
            String url,
            String attribution,
            boolean redistribute,
            boolean commercial,
            String file) {
    }

    /** {@code program} and {@code drumkit} are -1 when that field was left out. */
    public record Instrument(
            String name,
            String source,
            int bank,
            int program,
            String preset,
            int drumkit,
            double gainDb,
            int transpose,
            String keyLow,
            String keyHigh,
            String outOfRange,
            List<String> fallbacks) {
        public Instrument {
            fallbacks = List.copyOf(fallbacks);
        }

        public boolean percussion() {
            return drumkit >= 0;
        }
    }
}
