package io.github.kristyancarvalho.brmgen.brmodelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrmodeloRuntimeInspectorTest {
  private final BrmodeloRuntimeInspector inspector = new BrmodeloRuntimeInspector();

  @TempDir Path temporaryDirectory;

  @Test
  void reportsMissingCapabilitiesForAnUnrelatedJar() throws Exception {
    Path jar = temporaryDirectory.resolve("unrelated.jar");
    try (OutputStream output = Files.newOutputStream(jar);
        JarOutputStream ignored = new JarOutputStream(output)) {}

    CompatibilityReport report = inspector.inspect(jar);

    assertThat(report.compatible()).isFalse();
    assertThat(report.missingCapabilities())
        .contains("controlador.Diagrama", "controlador.apoios.GuardaPadraoBrM");
    assertThatThrownBy(() -> inspector.requireCompatible(jar))
        .isInstanceOf(BrmodeloException.class)
        .hasMessageContaining("error[E204]");
  }
}
