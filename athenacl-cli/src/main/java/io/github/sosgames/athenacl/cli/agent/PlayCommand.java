// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.Config;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.Issue;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.Severity;
import io.github.sosgames.athenacl.cli.model.TextPaths;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import io.github.sosgames.athenacl.render.SpeakerPlayer;
import io.github.sosgames.athenacl.render.WavRenderer;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankRejected;
import io.github.sosgames.athenacl.render.soundbank.SoundbankResolver;
import picocli.CommandLine;

import javax.sound.midi.MidiUnavailableException;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Plays a note list, an event log, or a WAV on the speakers. The command blocks until the
 * sound finishes. {@code --dry-run} prints the length and does not open the audio device.
 */
@CommandLine.Command(name = "play", description = "Play a note list, event log, or WAV on the speakers.")
public class PlayCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<notes.yaml|events.jsonl|wav|dir>")
    Path input;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;
    @CommandLine.Option(names = "--soundbank", paramLabel = "<id|file>")
    String soundbank;
    @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
    Path cache;

    @Override
    public Integer call() {
        parent.commandName = "play";
        if (input == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                    "usage: play <notes.yaml|events.jsonl|wav|dir>",
                    "Try play samples/notes/c-major-scale.yaml.");
        }
        if (!Files.exists(input)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]",
                    "file not found",
                    "Pass a note list, events.jsonl, score.wav, or the directory render wrote.");
        }
        try {
            if (isWav(input) || Files.isDirectory(input)) {
                return playWav();
            }
            return playScore();
        } catch (ScoreException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]", e.getMessage(), e.fix());
        } catch (UnsupportedAudioFileException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]",
                    "not a WAV file", "Pass score.wav, or a note list so play can render one.");
        } catch (LineUnavailableException e) {
            return parent.fail(ExitCode.ENVIRONMENT, "AUDIO_DEVICE", "speakers",
                    "no audio output device",
                    "Plug in speakers or headphones and try again.");
        } catch (MidiUnavailableException e) {
            return parent.fail(ExitCode.ENVIRONMENT, "GERVILL_ABSENT", "gervill",
                    "this JDK cannot open an offline Gervill stream",
                    "Use a JDK that includes com.sun.media.sound and keep --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED.");
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the file is readable and try again.");
        }
    }

    private Integer playWav() throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        Path wav = Files.isDirectory(input) ? input.resolve("score.wav") : input;
        if (!Files.isRegularFile(wav)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "wav not found",
                    "Render with --formats wav, then pass that directory or score.wav.");
        }
        SpeakerPlayer.Played played = SpeakerPlayer.play(wav, parent.dryRun);
        return finish(TextPaths.portable(wav), played, null);
    }

    private Integer playScore()
            throws IOException, ScoreException, MidiUnavailableException, UnsupportedAudioFileException,
            LineUnavailableException {
        Score score = input.getFileName().toString().endsWith(".jsonl")
                ? NoteFiles.events(input)
                : NoteFiles.yaml(input, seed);
        SoundbankResolver.Selection selection;
        try {
            String request = soundbank != null ? soundbank : Config.get("athenacl.soundbank");
            selection = SoundbankResolver.select(request, SoundbankPaths.cache(cache));
        } catch (SoundbankRejected e) {
            return parent.fail(ExitCode.INVALID_INPUT, e.code(), e.path(), e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--soundbank", e.getMessage(),
                    "Check that the soundbank file is readable.");
        }
        SpeakerPlayer.Played played;
        if (parent.dryRun) {
            played = new SpeakerPlayer.Played(WavRenderer.durationSeconds(score), true);
        } else {
            byte[] wav = WavRenderer.write(score, selection.file());
            played = SpeakerPlayer.play(wav, false);
        }
        return finish(TextPaths.portable(input), played, selection);
    }

    private Integer finish(String source, SpeakerPlayer.Played played, SoundbankResolver.Selection selection) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("action", "play");
        fields.put("source", source);
        fields.put("seconds", String.format(Locale.ROOT, "%.3f", played.seconds()));
        fields.put("dryRun", Boolean.toString(played.dryRun()));
        if (selection != null) {
            fields.put("soundbank", selection.name());
            if (selection.fallback()) {
                fields.put("warning", "SOUNDBANK_FALLBACK");
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", "play");
        data.put("source", source);
        data.put("seconds", played.seconds());
        data.put("dryRun", played.dryRun());
        if (selection != null) {
            data.put("soundbank", selection.name());
        }
        List<Issue> warnings = selection != null && selection.fallback()
                ? List.of(new Issue(
                "SOUNDBANK_FALLBACK",
                Severity.WARNING,
                "--soundbank",
                "soundbank fell back to the emergency bank",
                "Install generaluser, or pass a path to an .sf2 file that exists.",
                "AGENTS.md#cli"))
                : List.of();
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.play/1",
                data, warnings, List.of());
    }

    private static boolean isWav(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav");
    }
}
