package io.github.kristyancarvalho.brmgen.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class RootCommandTest {
  @TempDir Path temporaryDirectory;

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

  @Test
  void printsRootHelpForLongAndShortOptions() {
    for (String option : new String[] {"--help", "-h"}) {
      StringWriter output = new StringWriter();
      CommandLine commandLine = new CommandLine(new RootCommand());
      commandLine.setOut(new PrintWriter(output));

      int exitCode = commandLine.execute(option);

      assertThat(exitCode).isZero();
      assertThat(output.toString())
          .contains("Usage: brmgen")
          .contains("build")
          .contains("validate")
          .contains("doctor")
          .contains("--help")
          .contains("--version")
          .contains("brmgen COMMAND --help");
    }
  }

  @Test
  void printsBuildHelpWithEveryBuildOption() {
    String help = help("build");

    assertThat(help)
        .contains("Usage: brmgen build")
        .contains("<input>")
        .contains("-o, --output")
        .contains("--brmodelo-jar")
        .contains("--logical")
        .contains("BRMODELO_JAR")
        .contains("Examples:");
  }

  @Test
  void printsValidateHelp() {
    String help = help("validate");

    assertThat(help)
        .contains("Usage: brmgen validate")
        .contains("<input>")
        .contains("without generating output")
        .contains("Example:");
  }

  @Test
  void printsDoctorHelp() {
    String help = help("doctor");

    assertThat(help)
        .contains("Usage: brmgen doctor")
        .contains("--brmodelo-jar")
        .contains("BRMODELO_JAR")
        .contains("Example:");
  }

  @Test
  void validatesAValidModel() throws Exception {
    Path input =
        Files.writeString(
            temporaryDirectory.resolve("valid.yaml"),
            "version: 1\ndiagram:\n  name: Valid\nentities: []\n");
    StringWriter output = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setOut(new PrintWriter(output));

    int exitCode = commandLine.execute("validate", input.toString());

    assertThat(exitCode).isZero();
    assertThat(output.toString()).contains("Valid model:").contains(input.toString());
  }

  @Test
  void printsValidationDiagnosticsWithoutAStackTrace() throws Exception {
    Path input =
        Files.writeString(
            temporaryDirectory.resolve("invalid.yaml"), "version: 2\ndiagram:\n  name: ''\n");
    StringWriter error = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setErr(new PrintWriter(error));

    int exitCode = commandLine.execute("validate", input.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(error.toString())
        .contains("error[E001]")
        .contains("error[E002]")
        .contains("suggestion:")
        .doesNotContain("Exception");
  }

  @Test
  void printsParseErrorsWithoutAStackTrace() throws Exception {
    Path input = Files.writeString(temporaryDirectory.resolve("broken.json"), "{");
    StringWriter error = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setErr(new PrintWriter(error));

    int exitCode = commandLine.execute("validate", input.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(error.toString()).contains("error[E107]").doesNotContain("Exception");
  }

  @Test
  void doctorReportsAnIncompatibleJarWithoutAStackTrace() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("unrelated.jar"));
    StringWriter output = new StringWriter();
    StringWriter error = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setOut(new PrintWriter(output));
    commandLine.setErr(new PrintWriter(error));

    int exitCode = commandLine.execute("doctor", "--brmodelo-jar", jar.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(output.toString()).contains("Java runtime: 21").contains("brModelo JAR:");
    assertThat(error.toString()).contains("error[E204]").doesNotContain("Exception");
  }

  @Test
  void buildRefusesToOverwriteAnExistingOutput() throws Exception {
    Path input =
        Files.writeString(
            temporaryDirectory.resolve("entity.yaml"),
            "version: 1\ndiagram:\n  name: Entity\nentities:\n  - name: Customer\n");
    Path output = Files.createFile(temporaryDirectory.resolve("entity.brM3"));
    StringWriter error = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setErr(new PrintWriter(error));

    int exitCode = commandLine.execute("build", input.toString(), "-o", output.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(error.toString()).contains("error[E206]").doesNotContain("Exception");
  }

  private String help(String command) {
    StringWriter output = new StringWriter();
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setOut(new PrintWriter(output));

    int exitCode = commandLine.execute(command, "--help");

    assertThat(exitCode).isZero();
    return output.toString();
  }
}
