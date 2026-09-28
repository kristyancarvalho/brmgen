package io.github.kristyancarvalho.brmgen.brmodelo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.brmodelo.logical.LogicalBrm3Writer;
import io.github.kristyancarvalho.brmgen.layout.LayoutEngine;
import io.github.kristyancarvalho.brmgen.layout.LogicalLayoutEngine;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.Table;
import io.github.kristyancarvalho.brmgen.parser.ModelParser;
import io.github.kristyancarvalho.brmgen.transform.LogicalTransformer;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceExamplesIntegrationTest {
  private final Path jar = Path.of(System.getenv("BRMODELO_JAR")).toAbsolutePath().normalize();
  private final ModelParser parser = new ModelParser();

  @TempDir Path temporaryDirectory;

  @Test
  void generatesAuthorBookConceptual() throws Exception {
    ConceptualModel model = conceptual("author-book");
    Path output = writeConceptual(model, "author-book-conceptual.brM3");

    withDiagram(
        output,
        diagram -> {
          List<?> items = items(diagram);
          assertThat(typeNames(items, "diagramas.conceitual.Entidade"))
              .containsExactlyInAnyOrder("Autor", "Livro");
          Object author = named(items, "diagramas.conceitual.Entidade", "Autor");
          assertThat(attributeNames(author))
              .containsExactlyInAnyOrder("Cod_autor", "Nome_autor", "nacionalidade");
          assertThat(relationshipCardinalities(items, "Escreve"))
              .containsExactlyInAnyOrderEntriesOf(Map.of("Autor", "C1N", "Livro", "C1N"));
        });
  }

  @Test
  void generatesAuthorBookLogical() throws Exception {
    Path output = writeLogical(logical("author-book"), "author-book-logical.brM3");

    withDiagram(
        output,
        diagram -> {
          List<?> tables = tables(diagram);
          assertThat(names(tables)).containsExactlyInAnyOrder("Autor", "Escreve", "Livro");
          Object association = named(tables, "diagramas.logico.Tabela", "Escreve");
          assertThat(columnStates(association))
              .containsExactlyInAnyOrder(
                  "fk_Livro_cod_livro:true:true", "fk_Autor_Cod_autor:true:true");
          assertThat(constraintTypes(association))
              .containsExactlyInAnyOrder("tpPK", "tpFK", "tpFK");
        });
  }

  @Test
  void generatesAthleteTeamConceptual() throws Exception {
    Path output = writeConceptual(conceptual("athlete-team"), "athlete-team-conceptual.brM3");

    withDiagram(
        output,
        diagram -> {
          List<?> items = items(diagram);
          assertThat(typeNames(items, "diagramas.conceitual.Entidade"))
              .containsExactlyInAnyOrder("Atleta", "Equipe");
          Object participates = named(items, "diagramas.conceitual.Relacionamento", "Participa");
          assertThat(attributeNames(participates)).containsExactly("data_inicio", "data_fim");
          assertThat(relationshipCardinalities(items, "Participa"))
              .containsExactlyInAnyOrderEntriesOf(Map.of("Atleta", "C1N", "Equipe", "C1N"));
        });
  }

  @Test
  void generatesAthleteTeamLogical() throws Exception {
    Path output = writeLogical(logical("athlete-team"), "athlete-team-logical.brM3");

    withDiagram(
        output,
        diagram -> {
          Object association = named(tables(diagram), "diagramas.logico.Tabela", "Participa");
          assertThat(columnNames(association))
              .containsExactly(
                  "fk_Atleta_id_atleta", "fk_Equipe_id_equipe", "data_inicio", "data_fim");
          assertThat(columnStates(association))
              .contains("fk_Atleta_id_atleta:true:true", "fk_Equipe_id_equipe:true:true");
        });
  }

  @Test
  void generatesRestaurantConceptual() throws Exception {
    Path output = writeConceptual(conceptual("restaurant"), "restaurant-conceptual.brM3");

    withDiagram(
        output,
        diagram -> {
          List<?> items = items(diagram);
          assertThat(typeNames(items, "diagramas.conceitual.Entidade"))
              .containsExactlyInAnyOrder(
                  "Restaurante", "Prato", "Cliente", "Pedido", "Pagamento", "Forma_pagamento");
          assertThat(typeNames(items, "diagramas.conceitual.Relacionamento"))
              .containsExactlyInAnyOrder("Oferece", "Contem", "Realiza", "Possui", "Utiliza");
          Object contains = named(items, "diagramas.conceitual.Relacionamento", "Contem");
          assertThat(attributeNames(contains)).containsExactly("quantidade", "observacao");
          assertThat(relationshipCardinalities(items, "Contem"))
              .containsExactlyInAnyOrderEntriesOf(Map.of("Prato", "C1N", "Pedido", "C0N"));
        });
  }

  @Test
  void generatesRestaurantLogical() throws Exception {
    Path output = writeLogical(logical("restaurant"), "restaurant-logical.brM3");

    withDiagram(output, diagram -> assertRestaurantLogical(tables(diagram)));
  }

  @Test
  void transformsRestaurantConceptualAndGeneratesLogical() throws Exception {
    LogicalModel transformed = new LogicalTransformer().transform(conceptual("restaurant"));
    Path output = writeLogical(transformed, "restaurant-transformed.brM3");

    assertThat(transformed.tables().stream().map(Table::name))
        .containsExactlyInAnyOrder(
            "Restaurante", "Prato", "Cliente", "Pedido", "Pagamento", "Forma_pagamento", "Contem");
    withDiagram(output, diagram -> assertRestaurantLogical(tables(diagram)));
  }

  private void assertRestaurantLogical(List<?> tables) {
    assertThat(names(tables))
        .containsExactlyInAnyOrder(
            "Restaurante", "Prato", "Cliente", "Pedido", "Pagamento", "Forma_pagamento", "Contem");
    assertThat(columnNames(named(tables, "diagramas.logico.Tabela", "Prato")))
        .contains("fk_Restaurante_id_restaurante");
    assertThat(columnNames(named(tables, "diagramas.logico.Tabela", "Pedido")))
        .contains("fk_Cliente_cpf");
    Object contains = named(tables, "diagramas.logico.Tabela", "Contem");
    assertThat(columnNames(contains))
        .contains("fk_Prato_id_prato", "fk_Pedido_id_pedido", "quantidade", "observacao");
    assertThat(constraintTypes(contains)).containsExactlyInAnyOrder("tpPK", "tpFK", "tpFK");
    assertThat(columnNames(named(tables, "diagramas.logico.Tabela", "Pagamento")))
        .contains("fk_Pedido_id_pedido", "fk_Forma_pagamento_id_forma");
  }

  private ConceptualModel conceptual(String example) throws Exception {
    return (ConceptualModel) parser.parse(example(example, "conceptual.yaml"));
  }

  private LogicalModel logical(String example) throws Exception {
    return (LogicalModel) parser.parse(example(example, "logical.yaml"));
  }

  private Path example(String directory, String file) {
    return Path.of("examples", directory, file).toAbsolutePath().normalize();
  }

  private Path writeConceptual(ConceptualModel model, String filename) throws Exception {
    Path output = temporaryDirectory.resolve(filename);
    new NativeBrm3Writer().write(new LayoutEngine().layout(model), jar, output);
    assertThat(Files.size(output)).isGreaterThan(0);
    return output;
  }

  private Path writeLogical(LogicalModel model, String filename) throws Exception {
    Path output = temporaryDirectory.resolve(filename);
    new LogicalBrm3Writer().write(new LogicalLayoutEngine().layout(model), jar, output);
    assertThat(Files.size(output)).isGreaterThan(0);
    return output;
  }

  private void withDiagram(Path output, Consumer<Object> assertion) throws Exception {
    try (URLClassLoader loader = BrmodeloRuntimeInspector.loader(jar);
        InputStream input = Files.newInputStream(output);
        ObjectInputStream stream = objectInput(input, loader)) {
      Object guard = stream.readObject();
      Object diagram = guard.getClass().getMethod("getDiagrama").invoke(guard);
      assertion.accept(diagram);
    }
  }

  private List<?> items(Object diagram) {
    return invokeList(diagram, "getListaDeItens");
  }

  private List<?> tables(Object diagram) {
    return invokeList(diagram, "getListaDeTabelas");
  }

  private List<?> invokeList(Object target, String method) {
    try {
      return (List<?>) target.getClass().getMethod(method).invoke(target);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private List<String> typeNames(List<?> items, String type) {
    return names(items.stream().filter(item -> item.getClass().getName().equals(type)).toList());
  }

  private List<String> names(List<?> items) {
    return items.stream().map(this::text).toList();
  }

  private Object named(List<?> items, String type, String name) {
    return items.stream()
        .filter(item -> item.getClass().getName().equals(type))
        .filter(item -> text(item).equals(name))
        .findFirst()
        .orElseThrow();
  }

  private String text(Object item) {
    try {
      return (String) item.getClass().getMethod("getTexto").invoke(item);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private List<String> attributeNames(Object owner) {
    return names(invokeList(owner, "getAtributos"));
  }

  private List<String> columnNames(Object table) {
    return names(invokeList(table, "getCampos"));
  }

  private List<String> columnStates(Object table) {
    return invokeList(table, "getCampos").stream().map(this::columnState).toList();
  }

  private String columnState(Object column) {
    try {
      boolean key = (boolean) column.getClass().getMethod("isKey").invoke(column);
      boolean foreignKey = (boolean) column.getClass().getMethod("isFkey").invoke(column);
      return text(column) + ":" + key + ":" + foreignKey;
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private List<String> constraintTypes(Object table) {
    return invokeList(table, "getConstraints").stream().map(this::constraintType).toList();
  }

  private String constraintType(Object constraint) {
    try {
      return constraint.getClass().getMethod("getTipo").invoke(constraint).toString();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private Map<String, String> relationshipCardinalities(List<?> items, String relationshipName) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Object item : items) {
      if (!item.getClass().getName().equals("diagramas.conceitual.Ligacao")) {
        continue;
      }
      Object first = invoke(item, "getFormaPontaA");
      Object second = invoke(item, "getFormaPontaB");
      Object relationship = null;
      Object entity = null;
      if (isNamedRelationship(first, relationshipName)) {
        relationship = first;
        entity = second;
      } else if (isNamedRelationship(second, relationshipName)) {
        relationship = second;
        entity = first;
      }
      if (relationship != null
          && entity != null
          && entity.getClass().getName().equals("diagramas.conceitual.Entidade")) {
        Object cardinality = invoke(item, "getCard");
        result.put(text(entity), invoke(cardinality, "getCard").toString());
      }
    }
    return result;
  }

  private boolean isNamedRelationship(Object item, String name) {
    return item != null
        && item.getClass().getName().equals("diagramas.conceitual.Relacionamento")
        && text(item).equals(name);
  }

  private Object invoke(Object target, String method) {
    try {
      return target.getClass().getMethod(method).invoke(target);
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
