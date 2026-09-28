package io.github.kristyancarvalho.brmgen.brmodelo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class BrmodeloJarResolver {
  public Path resolve(Path explicitPath, Map<String, String> environment) throws BrmodeloException {
    String environmentPath = environment.get("BRMODELO_JAR");
    Path selected =
        explicitPath != null
            ? explicitPath
            : environmentPath == null || environmentPath.isBlank()
                ? null
                : Path.of(environmentPath);
    if (selected == null) {
      throw new BrmodeloException(
          "error[E200]: brModelo JAR is required; use --brmodelo-jar <path> or BRMODELO_JAR");
    }
    Path normalized = selected.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
      throw new BrmodeloException(
          "error[E201]: brModelo JAR is not a readable regular file: `" + normalized + "`");
    }
    if (!normalized.getFileName().toString().toLowerCase().endsWith(".jar")) {
      throw new BrmodeloException(
          "error[E202]: brModelo runtime path must identify a .jar file: `" + normalized + "`");
    }
    return normalized;
  }
}
