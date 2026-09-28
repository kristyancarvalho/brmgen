package io.github.kristyancarvalho.brmgen.cli;

import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.parser.ModelParseException;
import io.github.kristyancarvalho.brmgen.parser.ModelParser;
import io.github.kristyancarvalho.brmgen.validation.Diagnostic;
import io.github.kristyancarvalho.brmgen.validation.ModelValidator;
import io.github.kristyancarvalho.brmgen.validation.ValidationResult;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "brmgen",
    description = "Generate native brModelo conceptual model files from YAML or JSON.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
      RootCommand.BuildCommand.class,
      RootCommand.ValidateCommand.class,
      RootCommand.DoctorCommand.class,
      RootCommand.VersionCommand.class
    })
public final class RootCommand implements Runnable {
  @picocli.CommandLine.Spec picocli.CommandLine.Model.CommandSpec spec;

  @Override
  public void run() {
    throw new picocli.CommandLine.ParameterException(spec.commandLine(), "A command is required.");
  }

  @Command(name = "build", description = "Generate a native .brM3 file.")
  static final class BuildCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "<input>")
    Path input;

    @Option(
        names = {"-o", "--output"},
        paramLabel = "<file>")
    Path output;

    @Option(names = "--brmodelo-jar", paramLabel = "<jar>")
    Path brmodeloJar;

    @Override
    public Integer call() {
      System.err.println("Native generation is not available in this development build.");
      return 2;
    }
  }

  @Command(name = "validate", description = "Validate a YAML or JSON model definition.")
  static final class ValidateCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "<input>")
    Path input;

    @picocli.CommandLine.Spec picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
      try {
        ModelDefinition model = new ModelParser().parse(input);
        ValidationResult result = new ModelValidator().validate(model);
        if (!result.isValid()) {
          PrintWriter error = spec.commandLine().getErr();
          for (Diagnostic diagnostic : result.diagnostics()) {
            error.println(diagnostic.render());
            error.println();
          }
          return 1;
        }
        spec.commandLine().getOut().println("Valid model: " + input.toAbsolutePath().normalize());
        return 0;
      } catch (ModelParseException exception) {
        spec.commandLine().getErr().println(exception.getMessage());
        return 1;
      }
    }
  }

  @Command(name = "doctor", description = "Check the Java and brModelo runtime setup.")
  static final class DoctorCommand implements Callable<Integer> {
    @Option(names = "--brmodelo-jar", paramLabel = "<jar>")
    Path brmodeloJar;

    @Override
    public Integer call() {
      System.out.printf("Java %s%n", Runtime.version().feature());
      System.err.println(
          "brModelo compatibility checks are not available in this development build.");
      return 2;
    }
  }

  @Command(name = "version", description = "Print the brmgen version.")
  static final class VersionCommand implements Runnable {
    @picocli.CommandLine.Spec picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
      PrintWriter out = spec.commandLine().getOut();
      out.println(VersionProvider.version());
    }
  }
}
