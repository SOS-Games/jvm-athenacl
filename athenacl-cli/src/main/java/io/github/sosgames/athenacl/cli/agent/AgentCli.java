// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.CliLog;
import io.github.sosgames.athenacl.cli.model.Envelopes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.Issue;
import io.github.sosgames.athenacl.cli.model.JsonOut;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.Result;
import io.github.sosgames.athenacl.cli.model.Severity;
import io.github.sosgames.athenacl.cli.model.Suggestions;
import picocli.CommandLine;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Headless {@code athenacl} entry point. No window and no prompts.
 * Shared flags ({@code --json}, {@code -v}, {@code -vv}, {@code --log-level}, {@code --dry-run},
 * {@code --strict}, {@code -h}) are read by {@link Globals}. picocli then dispatches the command
 * name and that command's own flags. Stdout is the result. Logs and the one-line error summary
 * go to stderr, except usage errors, which also print this help page on stdout.
 * Commands: help, version, schema, render, play, describe-music, debug, soundbank, describe, analyze, theory, rhythm, theme, and preset.
 */
@CommandLine.Command(name = "athenacl", mixinStandardHelpOptions = false,
        subcommands = {
                HelpCommand.class, VersionCommand.class, SchemaCommand.class,
                RenderCommand.class, PlayCommand.class, DescribeMusicCommand.class, DebugCommand.class,
                SoundbankCommand.class, DescribeCommand.class, AnalyzeCommand.class,
                TheoryCommand.class, RhythmCommand.class, ThemeCommand.class, PresetCommand.class
        },
        description = "Headless athenaCL CLI.")
public class AgentCli implements Callable<Integer> {
    boolean json;
    boolean dryRun;
    boolean strict;
    PrintStream out = System.out;
    PrintStream err = System.err;
    long startedNanos = System.nanoTime();
    String commandName = "athenacl";

    public static void main(String[] args) {
        System.exit(execute(args, System.out, System.err));
    }

    public static int execute(String[] args, PrintStream out, PrintStream err) {
        AgentCli cli = new AgentCli();
        cli.out = out;
        cli.err = err;
        cli.startedNanos = System.nanoTime();
        CliLog.configure(CliLog.Level.WARN, err);
        try {
            return cli.dispatch(args == null ? new String[0] : args);
        } catch (Throwable thrown) {
            thrown.printStackTrace(err);
            return ExitCode.INTERNAL.number();
        } finally {
            CliLog.configure(CliLog.Level.WARN, System.err);
        }
    }

    @Override
    public Integer call() {
        commandName = "help";
        return HelpCommand.run(this, null);
    }

    private int dispatch(String[] args) {
        Globals flags = Globals.parse(args);
        json = flags.json;
        dryRun = flags.dryRun;
        strict = flags.strict;
        if (flags.error != null) {
            return fail(ExitCode.USAGE, "USAGE", "argv", flags.error,
                    "Use error, warn, info, debug, or trace.");
        }
        CliLog.Level level;
        try {
            level = CliLog.resolve(flags.logLevel, flags.verbose, flags.veryVerbose);
        } catch (IllegalArgumentException bad) {
            return fail(ExitCode.USAGE, "USAGE", "argv", bad.getMessage(),
                    "Use error, warn, info, debug, or trace.");
        }
        CliLog.configure(level, err);
        if (flags.helpRequested) {
            commandName = "help";
            logStart("help");
            return HelpCommand.run(this, Globals.firstCommand(flags.rest));
        }
        String guessed = Globals.firstCommand(flags.rest);
        commandName = guessed == null ? "help" : guessed;
        logStart(commandName);
        CommandLine cmd = new CommandLine(this);
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(err, true));
        cmd.setColorScheme(new CommandLine.Help.ColorScheme.Builder()
                .ansi(CommandLine.Help.Ansi.OFF).build());
        cmd.setPosixClusteredShortOptionsAllowed(false);
        cmd.setSubcommandsCaseInsensitive(true);
        cmd.setExpandAtFiles(false);
        cmd.setParameterExceptionHandler((ex, raw) -> onParseError(ex, raw));
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            ex.printStackTrace(err);
            return ExitCode.INTERNAL.number();
        });
        return cmd.execute(flags.rest.toArray(String[]::new));
    }

    private void logStart(String name) {
        String message = "start command=" + name;
        CliLog.debug("athenacl.cli", message);
        CliLog.trace("athenacl.cli", message);
    }

    private int onParseError(CommandLine.ParameterException ex, String[] raw) {
        String unknown = Globals.unknownCommand(raw);
        if (unknown != null) {
            commandName = unknown;
            String fix = "Run athenacl help to list commands.";
            String near = Suggestions.nearest(unknown, CommandCatalog.names());
            if (near != null) {
                fix = "Did you mean " + near + "? " + fix;
            }
            return fail(ExitCode.USAGE, "UNKNOWN_COMMAND", "argv[0]",
                    "unknown command: " + unknown, fix);
        }
        String message = ex.getMessage() == null ? "usage error" : ex.getMessage();
        int newline = message.indexOf('\n');
        if (newline >= 0) {
            message = message.substring(0, newline);
        }
        return fail(ExitCode.USAGE, "USAGE", "argv", message,
                "Run athenacl help to list commands and flags.");
    }

    int finish(ExitCode code, String text, String schema, Object data,
               List<Issue> warnings, List<Issue> errors) {
        List<Issue> warningList = warnings == null ? List.of() : List.copyOf(warnings);
        List<Issue> errorList = errors == null ? List.of() : List.copyOf(errors);
        boolean promote = code == ExitCode.OK && strict && !warningList.isEmpty();
        Result result = Envelopes.make(schema, code == ExitCode.OK && !promote, commandName,
                data, warningList, errorList, startedNanos);
        if (json) {
            JsonOut.write(out, result);
        } else if (text != null) {
            out.println(text);
        }
        if (!json) {
            for (Issue issue : warningList) {
                err.println(KeyValue.record("warning", KeyValue.issueFields(issue)));
            }
        }
        return promote ? ExitCode.INVALID_INPUT.number() : code.number();
    }

    int fail(ExitCode code, String errorCode, String path, String message, String fix) {
        Issue issue = new Issue(errorCode, Severity.ERROR, path, message, fix, "AGENTS.md#cli");
        Result result = Envelopes.make("athenacl.result.error/1", false, commandName, null,
                List.of(), List.of(issue), startedNanos);
        String text = KeyValue.record("error", KeyValue.issueFields(issue));
        err.println(message);
        if (json) {
            JsonOut.write(out, result);
        } else if (code == ExitCode.USAGE) {
            err.println(text);
            out.print(HelpText.full());
        } else {
            out.println(text);
        }
        return code.number();
    }
}
