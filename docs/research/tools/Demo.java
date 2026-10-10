import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.sun.media.sound.AudioSynthesizer;
import com.sun.media.sound.ModelPatch;

/** Builds a ~28 s GM demo, renders it with a given soundbank (or Gervill's default), dumps instruments. */
public class Demo {
  static final int PPQ = 480; // 120 bpm => 1 beat = 0.5 s
  static Track tr;
  static void msg(int cmd, int ch, int d1, int d2, long tick) throws Exception {
    tr.add(new MidiEvent(new ShortMessage(cmd, ch, d1, d2), tick));
  }
  static void note(int ch, int key, int vel, double startBeat, double lenBeats) throws Exception {
    msg(ShortMessage.NOTE_ON, ch, key, vel, (long)(startBeat*PPQ));
    msg(ShortMessage.NOTE_OFF, ch, key, 0, (long)((startBeat+lenBeats)*PPQ));
  }
  static void setup(int ch, int program) throws Exception {
    msg(ShortMessage.CONTROL_CHANGE, ch, 0, 0, 0);   // bank select MSB 0
    msg(ShortMessage.CONTROL_CHANGE, ch, 32, 0, 0);  // LSB 0
    msg(ShortMessage.PROGRAM_CHANGE, ch, program, 0, 0);
    msg(ShortMessage.CONTROL_CHANGE, ch, 7, 100, 0); // volume
    msg(ShortMessage.CONTROL_CHANGE, ch, 10, 64, 0); // pan centre
  }
  /** Sections, 8 beats (4 s) each: 0 piano, 1 strings, 2 brass, 3 flute, 4 guitar, 5 bass+drums, 6 drums solo. */
  static Sequence build() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, PPQ);
    tr = seq.createTrack();
    MetaMessage tempo = new MetaMessage(0x51, new byte[]{0x07, (byte)0xA1, 0x20}, 3); // 500000 us/qn
    tr.add(new MidiEvent(tempo, 0));
    setup(0, 0); setup(1, 48); setup(2, 61); setup(3, 73); setup(4, 24); setup(5, 33);
    msg(ShortMessage.CONTROL_CHANGE, 9, 7, 100, 0);
    double s;
    // piano: C major arpeggio + chord
    s = 0; int[] pf = {60,64,67,72,76,72,67,64};
    for (int i = 0; i < 6; i++) note(0, pf[i], 90, s+i*0.5, 0.5);
    for (int k : new int[]{48,60,64,67,72}) note(0, k, 85, s+3, 4.5);
    // strings: sustained chords
    s = 8; note(1,48,80,s,4); note(1,55,80,s,4); note(1,64,80,s,4); note(1,72,80,s,4);
    note(1,53,80,s+4,3.8); note(1,57,80,s+4,3.8); note(1,65,80,s+4,3.8); note(1,69,80,s+4,3.8);
    // brass: fanfare
    s = 16;
    double t = s; int[] bk = {60,64,67,72,67,72}; double[] bl = {0.5,0.5,0.5,1.5,0.5,3};
    for (int i = 0; i < bk.length; i++) { note(2,bk[i],100,t,bl[i]*0.95); note(2,bk[i]-12,90,t,bl[i]*0.95); t += bl[i]; }
    // flute: melody
    s = 24; int[] fl = {79,81,83,84,83,81,79,76}; double[] flen = {0.5,0.5,0.5,1.5,0.5,0.5,1,3};
    t = s; for (int i = 0; i < fl.length; i++) { note(3,fl[i],90,t,flen[i]*0.95); t += flen[i]; }
    // guitar: strummed chords (staggered)
    s = 32; int[][] chords = {{40,47,52,55,59,64},{45,52,57,60,64},{43,47,50,55,59,67},{40,47,52,55,59,64}};
    for (int c = 0; c < 4; c++) for (int j = 0; j < chords[c].length; j++) note(4, chords[c][j], 85, s+c*2+j*0.04, 1.9-j*0.04);
    // bass + drums groove
    s = 40; int[] bs = {36,36,43,43,41,41,43,31};
    for (int i = 0; i < 8; i++) note(5, bs[i], 100, s+i, 0.9);
    // drums: 2 bars with bass, 2 bars solo with fill (ch 10 = index 9)
    for (int bar = 0; bar < 4; bar++) {
      double b = 40 + bar*4;
      for (int e = 0; e < 8; e++) note(9, 42, 70, b+e*0.5, 0.25);            // closed hi-hat
      note(9,36,110,b,0.25); note(9,36,100,b+2,0.25); note(9,36,90,b+2.5,0.25); // kick
      note(9,38,105,b+1,0.25); note(9,38,105,b+3,0.25);                        // snare
      if (bar == 3) { note(9,50,100,b+3,0.25); note(9,47,100,b+3.25,0.25); note(9,45,100,b+3.5,0.25); note(9,41,100,b+3.75,0.25); }
    }
    note(9,49,110,56,1); note(9,36,110,56,0.25);  // crash on downbeat at 28 s
    MetaMessage eot = new MetaMessage(0x2F, new byte[0], 0);
    tr.add(new MidiEvent(eot, 60L*PPQ));             // end at 30 s
    return seq;
  }

  static void render(Sequence seq, Soundbank bank, File out, double seconds) throws Exception {
    AudioSynthesizer synth = (AudioSynthesizer) MidiSystem.getSynthesizer();
    AudioFormat fmt = new AudioFormat(44100, 16, 2, true, false);
    Map<String,Object> info = new HashMap<>();
    info.put("load default soundbank", bank == null);
    AudioInputStream ais = synth.openStream(fmt, info);
    if (bank != null) synth.loadAllInstruments(bank);
    Receiver r = synth.getReceiver();
    double usPerTick = 500000.0 / seq.getResolution();
    for (Track t : seq.getTracks()) for (int i = 0; i < t.size(); i++) {
      MidiEvent e = t.get(i);
      if (e.getMessage() instanceof ShortMessage) r.send(e.getMessage(), (long)(e.getTick()*usPerTick));
    }
    AudioSystem.write(new AudioInputStream(ais, fmt, (long)(44100*seconds)), AudioFileFormat.Type.WAVE, out);
    synth.close();
  }

  static void dump(Soundbank sb, File csv) throws Exception {
    try (PrintWriter w = new PrintWriter(csv, "UTF-8")) {
      w.println("bank,program,percussion,name");
      Instrument[] ins = sb.getInstruments().clone();
      Arrays.sort(ins, Comparator.comparingInt((Instrument i) -> isPerc(i)?1:0)
          .thenComparingInt(i -> i.getPatch().getBank()).thenComparingInt(i -> i.getPatch().getProgram()));
      for (Instrument i : ins) {
        Patch p = i.getPatch();
        w.printf("%d,%d,%s,\"%s\"%n", p.getBank(), p.getProgram(), isPerc(i), i.getName().trim().replace("\"","'"));
      }
    }
  }
  static boolean isPerc(Instrument i) { return i.getPatch() instanceof ModelPatch mp && mp.isPercussion(); }

  public static void main(String[] a) throws Exception {
    String label = a[0];
    Sequence seq = build();
    MidiSystem.write(seq, 1, new File("/workspace/research/audio/demo.mid"));
    Soundbank sb = a.length > 1 ? MidiSystem.getSoundbank(new File(a[1])) : MidiSystem.getSynthesizer().getDefaultSoundbank();
    System.out.println(label + ": " + sb.getName() + " | " + sb.getVendor() + " | instruments=" + sb.getInstruments().length);
    dump(sb, new File("/workspace/research/audio/instruments-" + label + ".csv"));
    long t0 = System.nanoTime();
    render(seq, a.length > 1 ? sb : null, new File("/workspace/jtest/demo-" + label + ".wav"), 31);
    System.out.printf("rendered in %.0f ms%n", (System.nanoTime()-t0)/1e6);
  }
}
