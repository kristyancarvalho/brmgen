package io.github.kristyancarvalho.brmgen.brmodelo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Relationship;
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

  @Test
  void roundTripsEntitiesAttributesRelationshipAndCardinalities() throws Exception {
    Path jar = Path.of(System.getenv("BRMODELO_JAR")).toAbsolutePath().normalize();
    Entity author =
        new Entity(
            "Autor",
            false,
            List.of(attribute("Cod_autor", true), attribute("Nome_autor", false)),
            new Position(80, 180));
    Entity book =
        new Entity(
            "Livro",
            false,
            List.of(attribute("cod_livro", true), attribute("titulo", false)),
            new Position(600, 180));
    Relationship writes =
        new Relationship(
            "Escreve",
            false,
            List.of(
                new Connection("Autor", Cardinality.ONE_TO_MANY),
                new Connection("Livro", Cardinality.ONE_TO_MANY)),
            List.of(attribute("desde", false)),
            new Position(340, 180));
    ModelDefinition model =
        new ModelDefinition(
            1, new Diagram("Biblioteca"), List.of(author, book), List.of(writes), List.of());
    Path output = temporaryDirectory.resolve("biblioteca.brM3");

    new NativeBrm3Writer().write(model, jar, output);

    try (URLClassLoader loader = BrmodeloRuntimeInspector.loader(jar);
        InputStream input = Files.newInputStream(output);
        ObjectInputStream stream = objectInput(input, loader)) {
      Object guard = stream.readObject();
      Object diagram = guard.getClass().getMethod("getDiagrama").invoke(guard);
      List<?> items = (List<?>) diagram.getClass().getMethod("getListaDeItens").invoke(diagram);
      List<?> entities = itemsOfType(items, "diagramas.conceitual.Entidade");
      List<?> relationships = itemsOfType(items, "diagramas.conceitual.Relacionamento");
      List<?> links = itemsOfType(items, "diagramas.conceitual.Ligacao");

      assertThat(names(entities)).containsExactlyInAnyOrder("Autor", "Livro");
      assertThat(relationships).hasSize(1);
      assertThat(text(relationships.getFirst())).isEqualTo("Escreve");
      assertThat(links.stream().map(this::endpointTypes))
          .contains("Entidade:Atributo", "Entidade:Relacionamento", "Relacionamento:Atributo");
      assertThat(attributeNames(findNamed(entities, "Autor")))
          .containsExactlyInAnyOrder("Cod_autor", "Nome_autor");
      assertThat(attributeNames(relationships.getFirst())).containsExactly("desde");
      assertThat(links).hasSize(7);
      assertThat(links.stream().map(this::cardinalityName).filter(name -> name != null))
          .containsExactlyInAnyOrder("C1N", "C1N");
    }
  }

  private Attribute attribute(String name, boolean key) {
    return new Attribute(name, key, false, false, false, false, List.of());
  }

  private List<?> itemsOfType(List<?> items, String type) {
    return items.stream().filter(item -> item.getClass().getName().equals(type)).toList();
  }

  private List<String> names(List<?> items) {
    return items.stream().map(this::text).toList();
  }

  private Object findNamed(List<?> items, String name) {
    return items.stream().filter(item -> text(item).equals(name)).findFirst().orElseThrow();
  }

  private String text(Object item) {
    try {
      return (String) item.getClass().getMethod("getTexto").invoke(item);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private List<String> attributeNames(Object owner) {
    try {
      List<?> attributes = (List<?>) owner.getClass().getMethod("getAtributos").invoke(owner);
      return names(attributes);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String cardinalityName(Object link) {
    try {
      Object cardinality = link.getClass().getMethod("getCard").invoke(link);
      if (cardinality == null) {
        return null;
      }
      Object value = cardinality.getClass().getMethod("getCard").invoke(cardinality);
      return value.toString();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String endpointTypes(Object link) {
    try {
      Object first = link.getClass().getMethod("getFormaPontaA").invoke(link);
      Object second = link.getClass().getMethod("getFormaPontaB").invoke(link);
      return first.getClass().getSimpleName() + ":" + second.getClass().getSimpleName();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
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
