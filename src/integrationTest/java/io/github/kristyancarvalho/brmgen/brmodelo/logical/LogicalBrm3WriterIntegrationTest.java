package io.github.kristyancarvalho.brmgen.brmodelo.logical;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.brmodelo.BrmodeloRuntimeInspector;
import io.github.kristyancarvalho.brmgen.layout.LogicalLayoutEngine;
import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.Column;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.ForeignKey;
import io.github.kristyancarvalho.brmgen.model.ForeignKeyReference;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.PrimaryKey;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import io.github.kristyancarvalho.brmgen.model.Table;
import io.github.kristyancarvalho.brmgen.transform.LogicalTransformer;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogicalBrm3WriterIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void roundTripsTablesCompositePrimaryKeyAndForeignKeys() throws Exception {
    Path jar = Path.of(System.getenv("BRMODELO_JAR")).toAbsolutePath().normalize();
    Table author = table("Autor", "Cod_autor");
    Table book = table("Livro", "cod_livro");
    Table writes =
        new Table(
            "Escreve",
            List.of(column("autor_id"), column("livro_id")),
            new PrimaryKey(List.of("autor_id", "livro_id")),
            List.of(
                foreignKey("autor_id", "Autor", "Cod_autor"),
                foreignKey("livro_id", "Livro", "cod_livro")),
            null);
    LogicalModel model =
        new LogicalLayoutEngine()
            .layout(new LogicalModel(1, new Diagram("Biblioteca"), List.of(author, book, writes)));
    Path output = temporaryDirectory.resolve("biblioteca-logical.brM3");

    new LogicalBrm3Writer().write(model, jar, output);

    try (URLClassLoader loader = BrmodeloRuntimeInspector.loader(jar);
        InputStream input = Files.newInputStream(output);
        ObjectInputStream stream = objectInput(input, loader)) {
      Object guard = stream.readObject();
      Object diagram = guard.getClass().getMethod("getDiagrama").invoke(guard);
      List<?> tables = (List<?>) diagram.getClass().getMethod("getListaDeTabelas").invoke(diagram);
      List<?> items = (List<?>) diagram.getClass().getMethod("getListaDeItens").invoke(diagram);

      assertThat(diagram.getClass().getName()).isEqualTo("diagramas.logico.DiagramaLogico");
      assertThat(tables.stream().map(this::text))
          .containsExactlyInAnyOrder("Autor", "Livro", "Escreve");
      Object nativeWrites = findNamed(tables, "Escreve");
      List<?> columns =
          (List<?>) nativeWrites.getClass().getMethod("getCampos").invoke(nativeWrites);
      assertThat(columns.stream().map(this::columnState))
          .containsExactlyInAnyOrder("autor_id:true:true", "livro_id:true:true");
      List<?> constraints =
          (List<?>) nativeWrites.getClass().getMethod("getConstraints").invoke(nativeWrites);
      assertThat(constraints.stream().map(this::constraintType))
          .containsExactlyInAnyOrder("tpPK", "tpFK", "tpFK");
      assertThat(constraints).allMatch(this::validatedConstraint);
      assertThat(
              constraints.stream().filter(constraint -> constraintType(constraint).equals("tpFK")))
          .allMatch(this::hasConstraintOrigin);
      assertThat(
              items.stream()
                  .filter(item -> item.getClass().getName().equals("diagramas.logico.LogicoLinha")))
          .hasSize(2);
      assertThat(Files.size(output)).isGreaterThan(0);
    }
  }

  @Test
  void transformsConceptualManyToManyAndRoundTripsNativeLogicalDiagram() throws Exception {
    Path jar = Path.of(System.getenv("BRMODELO_JAR")).toAbsolutePath().normalize();
    ConceptualModel conceptual =
        new ConceptualModel(
            1,
            new Diagram("Esportes"),
            List.of(entity("Atleta", "id_atleta"), entity("Equipe", "id_equipe")),
            List.of(
                new Relationship(
                    "Participa",
                    false,
                    List.of(
                        new Connection("Atleta", Cardinality.ONE_TO_MANY),
                        new Connection("Equipe", Cardinality.ONE_TO_MANY)),
                    List.of(attribute("data_inicio"), attribute("data_fim")),
                    null)),
            List.of());
    LogicalModel logical =
        new LogicalLayoutEngine().layout(new LogicalTransformer().transform(conceptual));
    Path output = temporaryDirectory.resolve("esportes-transformed.brM3");

    new LogicalBrm3Writer().write(logical, jar, output);

    try (URLClassLoader loader = BrmodeloRuntimeInspector.loader(jar);
        InputStream input = Files.newInputStream(output);
        ObjectInputStream stream = objectInput(input, loader)) {
      Object guard = stream.readObject();
      Object diagram = guard.getClass().getMethod("getDiagrama").invoke(guard);
      List<?> tables = (List<?>) diagram.getClass().getMethod("getListaDeTabelas").invoke(diagram);
      Object association = findNamed(tables, "Participa");
      List<?> columns = (List<?>) association.getClass().getMethod("getCampos").invoke(association);

      assertThat(columns.stream().map(this::text))
          .containsExactly("fk_Atleta_id_atleta", "fk_Equipe_id_equipe", "data_inicio", "data_fim");
      assertThat(columns.stream().map(this::columnState))
          .contains("fk_Atleta_id_atleta:true:true", "fk_Equipe_id_equipe:true:true");
    }
  }

  private Table table(String name, String key) {
    return new Table(name, List.of(column(key)), new PrimaryKey(List.of(key)), List.of(), null);
  }

  private Entity entity(String name, String key) {
    return new Entity(
        name,
        false,
        List.of(new Attribute(key, true, false, false, false, false, List.of())),
        null);
  }

  private Attribute attribute(String name) {
    return new Attribute(name, false, false, false, false, false, List.of());
  }

  private Column column(String name) {
    return new Column(name, "integer", false, false);
  }

  private ForeignKey foreignKey(String local, String table, String referenced) {
    return new ForeignKey(
        null, List.of(local), new ForeignKeyReference(table, List.of(referenced)), null, null);
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

  private String columnState(Object column) {
    try {
      String name = (String) column.getClass().getMethod("getTexto").invoke(column);
      boolean key = (boolean) column.getClass().getMethod("isKey").invoke(column);
      boolean foreignKey = (boolean) column.getClass().getMethod("isFkey").invoke(column);
      return name + ":" + key + ":" + foreignKey;
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String constraintType(Object constraint) {
    try {
      return constraint.getClass().getMethod("getTipo").invoke(constraint).toString();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private boolean validatedConstraint(Object constraint) {
    try {
      return (boolean) constraint.getClass().getMethod("isValidado").invoke(constraint);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private boolean hasConstraintOrigin(Object constraint) {
    try {
      return constraint.getClass().getMethod("getConstraintOrigem").invoke(constraint) != null;
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
