import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import com.sun.media.sound.AudioSynthesizer;

/**
 * Proof of concept: adaptive music, generated one bar at a time, with state changes quantized to
 * the next bar line. It is driven by a scripted "game" timeline and renders offline through Gervill,
 * using exactly the timestamped Receiver path a real-time version would use.
 *
 * Run: java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED AdaptivePoc.java <soundbank.sf2> <out.wav>
 */
public class AdaptivePoc {

    // ---- theme config (would be YAML/JSON in the library) ---------------------------------------
    record Layer(String role, int channel, int program, int minIntensityPct) {}
    record StateSpec(String name, int bpm, int root, int[] scale, int[][] progression,
                     int[][] rhythmCells, double baseDensity, int melodyLow, int melodyHigh,
                     int velocity, List<Layer> layers) {}

    static final int[] MAJOR_PENTA = {0, 2, 4, 7, 9};
    static final int[] DORIAN = {0, 2, 3, 5, 7, 9, 10};
    static final int[] HARM_MINOR = {0, 2, 3, 5, 7, 8, 11};

    // "fantasy" theme: GM programs; chords as scale-degree triads (0-based degree of the root)
    static final Map<String, StateSpec> FANTASY = Map.of(
        "peaceful", new StateSpec("peaceful", 84, 60, MAJOR_PENTA,
            new int[][]{{0}, {3}, {4}, {0}, {2}, {3}, {1}, {0}},
            new int[][]{{2, 2}, {1, 1, 2}, {3, 1}, {2, 1, 1}},           // in eighths per beat-pair
            0.55, 67, 84, 92,
            List.of(new Layer("pad", 0, 49, 0), new Layer("bass", 1, 32, 0),       // slow strings, acoustic bass
                    new Layer("melody", 2, 73, 0), new Layer("arp", 3, 46, 40))), // flute, harp
        "sneaking", new StateSpec("sneaking", 100, 62, DORIAN,
            new int[][]{{0}, {0}, {6}, {0}, {3}, {2}, {6}, {4}},
            new int[][]{{1, 1, 1, 1}, {1, 3}, {2, 1, 1}, {1, 1, 2}},
            0.35, 62, 76, 88,
            List.of(new Layer("pad", 0, 89, 0), new Layer("bass", 1, 45, 0),       // warm pad, pizzicato
                    new Layer("melody", 2, 71, 0), new Layer("perc", 9, 0, 30))), // clarinet, light kit
        "combat", new StateSpec("combat", 144, 62, HARM_MINOR,
            new int[][]{{0}, {5}, {3}, {4}, {0}, {5}, {6}, {4}},
            new int[][]{{1, 1, 1, 1}, {1, 1, 2}, {2, 1, 1}, {1, 1, 1, 1}},
            0.85, 69, 86, 105,
            List.of(new Layer("pad", 0, 48, 0), new Layer("bass", 1, 43, 0),       // strings, contrabass
                    new Layer("melody", 2, 61, 0), new Layer("perc", 9, 0, 0),    // brass section, kit
                    new Layer("ostinato", 3, 47, 50)))                          // timpani
    );

    // per-state channel volume (CC7): a first-pass loudness balance, tuned from measured RMS
    static final Map<String, Integer> MIX_VOLUME = Map.of("peaceful", 127, "sneaking", 127, "combat", 84);

    // ---- engine state ---------------------------------------------------------------------------
    final Receiver rx;
    final long seed;
    StateSpec current;
    String pendingState;            // applied at the next bar line
    double intensity = 0.3, pendingIntensity = 0.3;
    long barStartUs = 0;
    int barIndex = 0, barInState = 0;
    int lastMelody = -1;
    final List<String> log = new ArrayList<>();

    AdaptivePoc(Receiver rx, long seed) { this.rx = rx; this.seed = seed; }

    // game-facing API
    void setState(String name, double intensity) { pendingState = name; pendingIntensity = intensity; }
    void setIntensity(double v) { pendingIntensity = v; }

    long barLengthUs(StateSpec s) { return Math.round(4 * 60_000_000.0 / s.bpm()); }

    void send(int cmd, int ch, int d1, int d2, long tUs) {
        try { rx.send(new ShortMessage(cmd, ch, d1, d2), tUs); } catch (InvalidMidiDataException e) { throw new RuntimeException(e); }
    }
    void note(int ch, int key, int vel, long onUs, long durUs) {
        key = Math.max(0, Math.min(127, key)); vel = Math.max(1, Math.min(127, vel));
        send(ShortMessage.NOTE_ON, ch, key, vel, onUs);
        send(ShortMessage.NOTE_OFF, ch, key, 0, onUs + Math.max(10_000, durUs));
    }

    /** Called once per bar boundary, by the scheduler, ahead of time (lookahead). */
    void scheduleNextBar() {
        boolean changed = false;
        String from = current == null ? "-" : current.name();
        if (pendingState != null && (current == null || !pendingState.equals(current.name()))) {
            StateSpec next = FANTASY.get(pendingState);
            transition(current, next, barStartUs);
            current = next; barInState = 0; changed = true;
        }
        intensity += (pendingIntensity - intensity) * 0.5;          // smooth intensity per bar
        StateSpec s = current;
        // deterministic per (seed, state, bar): same inputs, same music
        Random r = new Random(Objects.hash(seed, s.name(), barIndex));
        long bar = barLengthUs(s), beat = bar / 4;
        int[] chord = s.progression()[barInState % s.progression().length];
        int deg = chord[0];
        int chordRoot = s.root() + pitchOfDegree(s.scale(), deg);
        int[] triad = {pitchOfDegree(s.scale(), deg), pitchOfDegree(s.scale(), deg + 2), pitchOfDegree(s.scale(), deg + 4)};

        for (Layer l : s.layers()) {
            if (intensity * 100 < l.minIntensityPct()) continue;   // vertical layering by intensity
            int vel = (int) (s.velocity() * (0.75 + 0.35 * intensity));
            switch (l.role()) {
                case "pad" -> { for (int p : triad) note(l.channel(), s.root() - 12 + p, vel - 25, barStartUs, bar - 20_000); }
                case "bass" -> {
                    if (s.name().equals("combat")) for (int i = 0; i < 8; i++) note(l.channel(), chordRoot - 24, vel - 5, barStartUs + i * beat / 2, beat / 2 - 30_000);
                    else if (s.name().equals("sneaking")) for (int i = 0; i < 4; i += 1) { if (r.nextDouble() < 0.7) note(l.channel(), chordRoot - 24 + (i == 2 ? 7 : 0), vel - 10, barStartUs + i * beat, beat / 3); }
                    else { note(l.channel(), chordRoot - 24, vel - 15, barStartUs, beat * 2); note(l.channel(), chordRoot - 17, vel - 20, barStartUs + 2 * beat, beat * 2); }
                }
                case "arp" -> { for (int i = 0; i < 8; i++) note(l.channel(), s.root() + triad[i % 3] + (i >= 3 && i < 6 ? 12 : 0), vel - 30, barStartUs + i * beat / 2, beat); }
                case "ostinato" -> { for (int i = 0; i < 4; i++) note(l.channel(), chordRoot - 24 + (i % 2 == 0 ? 0 : 7), vel, barStartUs + i * beat, beat / 2); }
                case "perc" -> percussion(s, r, vel, bar, beat);
                case "melody" -> melody(s, l, r, vel, chordRoot, bar);
            }
        }
        log.add(String.format(Locale.ROOT, "bar %2d  t=%6.2fs  %-8s bpm=%3d intensity=%.2f%s", barIndex, barStartUs / 1e6,
                s.name(), s.bpm(), intensity, changed ? "   <- transition from " + from : ""));
        barStartUs += bar; barIndex++; barInState++;
    }

    void melody(StateSpec s, Layer l, Random r, int vel, int chordRoot, long bar) {
        long eighth = bar / 8, t = 0;
        int[] cell = s.rhythmCells()[r.nextInt(s.rhythmCells().length)];
        int idx = 0;
        while (t < bar) {
            long dur = cell[idx % cell.length] * eighth; idx++;
            double density = s.baseDensity() * (0.6 + 0.6 * intensity);
            if (r.nextDouble() < density) {
                int target = lastMelody < 0 ? chordRoot + 12 : lastMelody + (r.nextInt(5) - 2) * (r.nextDouble() < 0.2 ? 2 : 1) * 2;
                int p = snapToScale(s, Math.max(s.melodyLow(), Math.min(s.melodyHigh(), target)));
                if (t == 0) p = snapToChord(s, p, chordRoot);         // downbeats on chord tones
                lastMelody = p;
                note(l.channel(), p, vel - (t == 0 ? 0 : 12), barStartUs + t, (long) (dur * (s.name().equals("sneaking") ? 0.5 : 0.92)));
            }
            t += dur;
        }
    }

    void percussion(StateSpec s, Random r, int vel, long bar, long beat) {
        if (s.name().equals("combat")) {
            for (int i = 0; i < 8; i++) note(9, 42, vel - 30, barStartUs + i * beat / 2, 50_000);         // closed hat
            note(9, 36, vel, barStartUs, 80_000); note(9, 36, vel - 10, barStartUs + 2 * beat + beat / 2, 80_000);
            note(9, 38, vel, barStartUs + beat, 80_000); note(9, 38, vel, barStartUs + 3 * beat, 80_000);
            if (intensity > 0.7 && r.nextDouble() < 0.5) for (int i = 0; i < 4; i++) note(9, 47 - i * 2, vel - 10, barStartUs + 3 * beat + i * beat / 4, 60_000);
        } else {
            for (int i = 0; i < 8; i++) if (r.nextDouble() < 0.6) note(9, 44, vel - 35, barStartUs + i * beat / 2, 40_000);   // pedal hat
            if (r.nextDouble() < 0.5) note(9, 37, vel - 30, barStartUs + 3 * beat + beat / 2, 40_000);                     // side stick
        }
    }

    /** Re-orchestration at the bar line: program changes, volume ramps, and a stinger into combat. */
    void transition(StateSpec from, StateSpec to, long atUs) {
        for (Layer l : to.layers()) {
            if (l.channel() != 9) send(ShortMessage.PROGRAM_CHANGE, l.channel(), l.program(), 0, atUs - 5_000);
            send(ShortMessage.CONTROL_CHANGE, l.channel(), 7, MIX_VOLUME.get(to.name()), atUs - 5_000);   // per-state mix gain
        }
        if (from != null) {   // release whatever the old state left sounding on channels the new one does not use
            for (Layer l : from.layers()) if (to.layers().stream().noneMatch(x -> x.channel() == l.channel()))
                send(ShortMessage.CONTROL_CHANGE, l.channel(), 123, 0, atUs - 5_000);   // all notes off
        }
        if (to.name().equals("combat")) {           // stinger on the downbeat
            note(9, 49, 120, atUs, 400_000); note(9, 57, 110, atUs, 400_000);
            note(4, to.root() - 12, 120, atUs, 600_000);
            send(ShortMessage.PROGRAM_CHANGE, 4, 55, 0, atUs - 5_000);   // orchestra hit
        }
    }

    static int pitchOfDegree(int[] scale, int deg) { return scale[Math.floorMod(deg, scale.length)] + 12 * Math.floorDiv(deg, scale.length); }
    static int snapToScale(StateSpec s, int p) {
        int best = p, bestD = 99;
        for (int o = -1; o <= 1; o++) for (int d : s.scale()) {
            int c = s.root() + d + 12 * (Math.floorDiv(p - s.root(), 12) + o);
            if (Math.abs(c - p) < bestD) { bestD = Math.abs(c - p); best = c; }
        }
        return best;
    }
    static int snapToChord(StateSpec s, int p, int chordRoot) {
        int best = p, bestD = 99;
        for (int o = -2; o <= 2; o++) for (int iv : new int[]{0, 3, 4, 7}) {
            int c = chordRoot + iv + 12 * o;
            if (snapToScale(s, c) == c && Math.abs(c - p) < bestD) { bestD = Math.abs(c - p); best = c; }
        }
        return best;
    }

    // ---- driver: scripted "game" + offline render through the same timestamped Receiver ------------
    public static void main(String[] a) throws Exception {
        String bank = a[0]; File out = new File(a[1]);
        AudioSynthesizer synth = (AudioSynthesizer) MidiSystem.getSynthesizer();
        AudioFormat fmt = new AudioFormat(44100, 16, 2, true, false);
        AudioInputStream audio = synth.openStream(fmt, Map.of("load default soundbank", false));
        synth.loadAllInstruments(MidiSystem.getSoundbank(new File(bank)));
        AdaptivePoc eng = new AdaptivePoc(synth.getReceiver(), 2026L);

        // game timeline: (seconds, action). Requests land mid-bar on purpose.
        record Cue(double t, String state, double intensity) {}
        List<Cue> game = List.of(new Cue(0, "peaceful", 0.3), new Cue(9.0, "peaceful", 0.7),
                                 new Cue(16.4, "sneaking", 0.4), new Cue(29.1, "combat", 0.6),
                                 new Cue(36.0, "combat", 1.0));
        double endSec = 46.0, lookaheadSec = 0.10;
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        byte[] buf = new byte[4 * 441];                       // 10 ms blocks, like an audio callback
        long framesRendered = 0; int cueIdx = 0;
        List<String> requests = new ArrayList<>();
        while (framesRendered < endSec * 44100) {
            double now = framesRendered / 44100.0;
            while (cueIdx < game.size() && game.get(cueIdx).t() <= now) {    // game -> engine
                Cue c = game.get(cueIdx++); eng.setState(c.state(), c.intensity());
                requests.add(String.format(Locale.ROOT, "t=%6.2fs  request %s @ %.1f", now, c.state(), c.intensity()));
            }
            // scheduler: generate the next bar when its start is within the lookahead window
            while (eng.barStartUs / 1e6 <= now + lookaheadSec) eng.scheduleNextBar();
            int n = audio.read(buf, 0, buf.length);
            pcm.write(buf, 0, n); framesRendered += n / 4;
        }
        // short tail
        for (int i = 0; i < 300; i++) { int n = audio.read(buf, 0, buf.length); pcm.write(buf, 0, n); }
        byte[] data = pcm.toByteArray();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(data), fmt, data.length / 4), AudioFileFormat.Type.WAVE, out);
        synth.close();
        requests.forEach(System.out::println);
        eng.log.forEach(System.out::println);
        System.out.printf(Locale.ROOT, "wrote %s (%.1f s)%n", out, data.length / 4 / 44100.0);
    }
}
