package io.github.kristyancarvalho.brmgen.cli;

import io.github.kristyancarvalho.brmgen.brmodelo.BrmodeloException;
import io.github.kristyancarvalho.brmgen.brmodelo.BrmodeloJarResolver;
import io.github.kristyancarvalho.brmgen.brmodelo.BrmodeloRuntimeInspector;
import io.github.kristyancarvalho.brmgen.brmodelo.CompatibilityReport;
import io.github.kristyancarvalho.brmgen.brmodelo.NativeBrm3Writer;
import io.github.kristyancarvalho.brmgen.brmodelo.logical.LogicalBrm3Writer;
import io.github.kristyancarvalho.brmgen.layout.LayoutEngine;
import io.github.kristyancarvalho.brmgen.layout.LogicalLayoutEngine;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.parser.ModelParseException;
import io.github.kristyancarvalho.brmgen.parser.ModelParser;
import io.github.kristyancarvalho.brmgen.transform.LogicalTransformer;
import io.github.kristyancarvalho.brmgen.transform.TransformationException;
import io.github.kristyancarvalho.brmgen.validation.Diagnostic;
import io.github.kristyancarvalho.brmgen.validation.LogicalModelValidator;
import io.github.kristyancarvalho.brmgen.validation.ModelValidator;
import io.github.kristyancarvalho.brmgen.validation.ValidationResult;
import java.io.PrintWriter;
import java.nio.file.Files;
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

    @Option(names = "--logical", description = "Transform conceptual input to logical output.")
    boolean logical;

    @picocli.CommandLine.Spec picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
      try {
        ModelDefinition model = new ModelParser().parse(input);
        ValidationResult validation = validate(model);
        if (!validation.isValid()) {
          printDiagnostics(validation, spec.commandLine().getErr());
          return 1;
        }
        if (logical && model instanceof ConceptualModel conceptual) {
          model = new LogicalTransformer().transform(conceptual);
          ValidationResult logicalValidation = validate(model);
          if (!logicalValidation.isValid()) {
            printDiagnostics(logicalValidation, spec.commandLine().getErr());
            return 1;
          }
        }
        Path destination =
            output == null ? defaultOutput(input) : output.toAbsolutePath().normalize();
        if (Files.exists(destination)) {
          spec.commandLine()
              .getErr()
              .println("error[E206]: output already exists: `" + destination + "`");
          return 1;
        }
        Path jar = new BrmodeloJarResolver().resolve(brmodeloJar, System.getenv());
        if (model instanceof ConceptualModel conceptual) {
          ConceptualModel positioned = new LayoutEngine().layout(conceptual);
          new NativeBrm3Writer().write(positioned, jar, destination);
        } else {
          LogicalModel positioned = new LogicalLayoutEngine().layout((LogicalModel) model);
          new LogicalBrm3Writer().write(positioned, jar, destination);
        }
        spec.commandLine().getOut().println("Generated: " + destination);
        return 0;
      } catch (ModelParseException | BrmodeloException | TransformationException exception) {
        spec.commandLine().getErr().println(exception.getMessage());
        return 1;
      }
    }

    private Path defaultOutput(Path source) {
      Path normalized = source.toAbsolutePath().normalize();
      String filename = normalized.getFileName().toString();
      int extension = filename.lastIndexOf('.');
      String base = extension > 0 ? filename.substring(0, extension) : filename;
      return normalized.resolveSibling(base + ".brM3");
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
        ValidationResult result = validate(model);
        if (!result.isValid()) {
          printDiagnostics(result, spec.commandLine().getErr());
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

    @picocli.CommandLine.Spec picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
      PrintWriter out = spec.commandLine().getOut();
      int javaVersion = Runtime.version().feature();
      out.println(
          "Java runtime: " + javaVersion + (javaVersion >= 21 ? " (ok)" : " (unsupported)"));
      if (javaVersion < 21) {
        spec.commandLine().getErr().println("error[E207]: Java 21 or newer is required");
        return 1;
      }
      try {
        Path jar = new BrmodeloJarResolver().resolve(brmodeloJar, System.getenv());
        CompatibilityReport report = new BrmodeloRuntimeInspector().inspect(jar);
        out.println("brModelo JAR: " + report.jar());
        out.println("Detected version: " + report.detectedVersion());
        if (!report.compatible()) {
          spec.commandLine()
              .getErr()
              .println(
                  "error[E204]: incompatible brModelo runtime; missing: "
                      + String.join(", ", report.missingCapabilities()));
          return 1;
        }
        out.println(
            "Compatibility: supported native conceptual and logical capabilities available");
        return 0;
      } catch (BrmodeloException exception) {
        spec.commandLine().getErr().println(exception.getMessage());
        return 1;
      }
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

  private static void printDiagnostics(ValidationResult result, PrintWriter error) {
    for (Diagnostic diagnostic : result.diagnostics()) {
      error.println(diagnostic.render());
      error.println();
    }
  }

  private static ValidationResult validate(ModelDefinition model) {
    if (model instanceof ConceptualModel conceptual) {
      return new ModelValidator().validate(conceptual);
    }
    return new LogicalModelValidator().validate((LogicalModel) model);
  }
}
