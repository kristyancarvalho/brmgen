package io.github.kristyancarvalho.brmgen.brmodelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrmodeloJarResolverTest {
  private final BrmodeloJarResolver resolver = new BrmodeloJarResolver();

  @TempDir Path temporaryDirectory;

  @Test
  void explicitPathTakesPrecedenceOverEnvironment() throws Exception {
    Path explicit = Files.createFile(temporaryDirectory.resolve("explicit.jar"));
    Path environment = Files.createFile(temporaryDirectory.resolve("environment.jar"));

    Path selected = resolver.resolve(explicit, Map.of("BRMODELO_JAR", environment.toString()));

    assertThat(selected).isEqualTo(explicit.toAbsolutePath());
  }

  @Test
  void usesEnvironmentWhenExplicitPathIsAbsent() throws Exception {
    Path environment = Files.createFile(temporaryDirectory.resolve("environment.jar"));

    Path selected = resolver.resolve(null, Map.of("BRMODELO_JAR", environment.toString()));

    assertThat(selected).isEqualTo(environment.toAbsolutePath());
  }

  @Test
  void reportsMissingConfiguration() {
    assertThatThrownBy(() -> resolver.resolve(null, Map.of()))
        .isInstanceOf(BrmodeloException.class)
        .hasMessageContaining("error[E200]")
        .hasMessageContaining("BRMODELO_JAR");
  }

  @Test
  void rejectsUnreadableOrNonJarPaths() throws Exception {
    Path text = Files.createFile(temporaryDirectory.resolve("runtime.txt"));

    assertThatThrownBy(() -> resolver.resolve(text, Map.of()))
        .isInstanceOf(BrmodeloException.class)
        .hasMessageContaining("error[E202]");
    assertThatThrownBy(() -> resolver.resolve(temporaryDirectory.resolve("missing.jar"), Map.of()))
        .isInstanceOf(BrmodeloException.class)
        .hasMessageContaining("error[E201]");
  }
}
