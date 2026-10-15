// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import io.github.sosgames.athenacl.render.soundbank.InstrumentIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureAnalysisTest {
    @Test
    void writesTheFixtureBank() throws Exception {
        String hash = FixtureBank.write(Path.of("samples/kits/analysis/fixtures.sf2"));
        assertEquals("d898dbf3121bf3ad3d716e53acbf59bbbb269c9cfdb3417a4f4a7cb802f5ada4", hash);
    }

    @Test
    void clippedIsLouderThanTheSaw() throws Exception {
        Descriptors.Measurement clipped = InstrumentProfile.probe(
                Path.of("samples/kits/analysis.yaml"), "clipped", 60, 120);
        Descriptors.Measurement saw = InstrumentProfile.probe(
                Path.of("samples/kits/analysis.yaml"), "saw", 60, 120);
        assertTrue(clipped.peakDbfs() >= -6.0, "clipped " + clipped.peakDbfs());
        assertTrue(saw.peakDbfs() < -8.0, "saw " + saw.peakDbfs());
    }

    @Test
    void threeFixturesAreBadAndTheSawIsBright() throws Exception {
        Files.createDirectories(Path.of("build/l7d-test"));
        Path profile = Path.of("build/l7d-test/analysis.profile.json");
        Files.deleteIfExists(profile);
        InstrumentProfile.Report report = InstrumentProfile.analyze(
                Path.of("samples/kits/analysis.yaml"), Path.of("build/l7d-test"));
        StringBuilder detail = new StringBuilder();
        List<String> bad = new ArrayList<>();
        for (InstrumentProfile.Row row : report.rows()) {
            detail.append(InstrumentProfile.rowLine(row, false)).append('\n');
            detail.append(InstrumentProfile.text(row)).append("\n---\n");
            if ("bad".equals(row.verdict())) {
                bad.add(row.name());
            }
        }
        assertEquals(7, report.analyzed(), detail.toString());
        assertEquals(List.of("inharmonic", "clipped", "loopclick"), bad, detail.toString());
        InstrumentProfile.Row saw = report.rows().get(0);
        assertEquals("ok", saw.verdict(), detail.toString());
        assertEquals(SoundClass.SYNTH, saw.soundClass(), detail.toString());
        assertTrue(detail.toString().contains("octave_offset="), detail.toString());
        assertTrue(saw.tags().contains("bright"), detail.toString());
        assertTrue(Math.abs(saw.pitchErrorCents()) <= 2, detail.toString());
        InstrumentProfile.Row pad = row(report, "pad");
        assertTrue(InstrumentProfile.text(pad).contains("slow attack"), detail.toString());
        assertTrue(row(report, "pluck").tags().contains("plucky"), detail.toString());
        assertTrue(!row(report, "inharmonic").tags().contains("plucky"), detail.toString());
        assertTrue(InstrumentProfile.text(pad).lines().count() <= 8);
        InstrumentProfile.Report again = InstrumentProfile.analyze(
                Path.of("samples/kits/analysis.yaml"), Path.of("build/l7d-test"));
        assertTrue(again.cached());
        assertEquals(report.hash(), again.hash());
    }

    @Test
    void descriptionNamesTheToneAndTheLevel() {
        assertTrue(textOf(3.2, -15, List.of("bright"), "").contains("The tone is bright."));
        assertTrue(textOf(3.2, -15, List.of("bright"), "").contains("The sound is loud."));
        assertTrue(textOf(1.1, -30, List.of("warm"), "").contains("The tone is dark."));
        assertTrue(textOf(1.1, -30, List.of("warm"), "").contains("The sound is quiet."));
        assertTrue(textOf(1.6, -22, List.of(), "").contains("The tone is neutral."));
        assertTrue(textOf(1.6, -22, List.of(), "").contains("The sound is average."));
        assertTrue(textOf(2.0, 0, List.of(), "loud trim=-1.0dB").contains("The sound is loud."));
    }

    private static String textOf(double centroid, double loudness, List<String> tags, String note) {
        return InstrumentProfile.text(new InstrumentProfile.Row(
                "piano", "ok", List.of(), tags, 0, 10, "measured",
                centroid, 0, 0, false, 0, loudness, "pitched-sustained", 0, note));
    }

    @Test
    void soundfontScanCoversEveryPreset() throws Exception {
        Path bank = Path.of("samples/kits/analysis/fixtures.sf2");
        FixtureBank.write(bank);
        Path out = Path.of("build/l7d-test/soundfonts");
        List<SoundfontScan.Hit> first = SoundfontScan.fill(bank, out, "fixtures", null);
        assertEquals(InstrumentIndex.list(bank).size(), first.size());
        assertTrue(first.stream().anyMatch(hit -> "saw".equals(hit.name()) && "ok".equals(hit.row().verdict())));
        List<SoundfontScan.Hit> again = SoundfontScan.open(bank, out, "fixtures");
        assertEquals(first.size(), again.size());
    }

    private static InstrumentProfile.Row row(InstrumentProfile.Report report, String name) {
        for (InstrumentProfile.Row row : report.rows()) {
            if (row.name().equals(name)) {
                return row;
            }
        }
        throw new AssertionError(name);
    }
}
