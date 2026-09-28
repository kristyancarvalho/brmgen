package io.github.kristyancarvalho.brmgen.brmodelo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.Position;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeBrm3WriterIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void generatesAndLoadsANativeEntityDiagram() throws Exception {
    Path jar = Path.of(System.getenv("BRMODELO_JAR")).toAbsolutePath().normalize();
    ModelDefinition model =
        new ModelDefinition(
            1,
            new Diagram("Customers"),
            List.of(new Entity("Customer", false, List.of(), new Position(100, 120))),
            List.of(),
            List.of());
    Path output = temporaryDirectory.resolve("customers.brM3");

    new NativeBrm3Writer().write(model, jar, output);

    assertThat(Files.size(output)).isGreaterThan(0);
    try (URLClassLoader loader = BrmodeloRuntimeInspector.loader(jar);
        InputStream input = Files.newInputStream(output);
        ObjectInputStream stream = objectInput(input, loader)) {
      Object guard = stream.readObject();
      Object diagram = guard.getClass().getMethod("getDiagrama").invoke(guard);
      List<?> items = (List<?>) diagram.getClass().getMethod("getListaDeItens").invoke(diagram);
      Object entity =
          items.stream()
              .filter(item -> item.getClass().getName().equals("diagramas.conceitual.Entidade"))
              .findFirst()
              .orElseThrow();

      assertThat(entity.getClass().getMethod("getTexto").invoke(entity)).isEqualTo("Customer");
      assertThat(entity.getClass().getMethod("getLocation").invoke(entity))
          .isEqualTo(new java.awt.Point(100, 120));
    }
  }

  private ObjectInputStream objectInput(InputStream input, ClassLoader loader) throws Exception {
    return new ObjectInputStream(input) {
      @Override
      protected Class<?> resolveClass(ObjectStreamClass descriptor)
          throws java.io.IOException, ClassNotFoundException {
        try {
          return Class.forName(descriptor.getName(), false, loader);
        } catch (ClassNotFoundException exception) {
          return super.resolveClass(descriptor);
        }
      }
    };
  }
}
