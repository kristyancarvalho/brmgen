package io.github.kristyancarvalho.brmgen.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class RootCommandTest {
  @Test
  void printsVersion() {
    StringWriter output = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setOut(new PrintWriter(output));

    int exitCode = commandLine.execute("version");

    assertThat(exitCode).isZero();
    assertThat(output.toString()).isEqualTo("brmgen development" + System.lineSeparator());
  }

  @Test
  void requiresACommand() {
    StringWriter error = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setErr(new PrintWriter(error));

    int exitCode = commandLine.execute();

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(error.toString()).contains("A command is required");
  }

  @Test
  void exposesMvpCommands() {
    CommandLine commandLine = new CommandLine(new RootCommand());

    assertThat(commandLine.getSubcommands()).containsKeys("build", "validate", "doctor", "version");
  }
}
