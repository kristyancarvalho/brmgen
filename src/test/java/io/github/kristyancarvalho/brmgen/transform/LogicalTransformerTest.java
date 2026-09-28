package io.github.kristyancarvalho.brmgen.transform;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import io.github.kristyancarvalho.brmgen.model.Table;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicalTransformerTest {
  private final LogicalTransformer transformer = new LogicalTransformer();

  @Test
  void transformsManyToManyIntoAssociativeTableWithRelationshipAttributes() throws Exception {
    ConceptualModel model =
        model(
            List.of(entity("Atleta", "id_atleta"), entity("Equipe", "id_equipe")),
            List.of(
                relationship(
                    "Participa",
                    "Atleta",
                    Cardinality.ONE_TO_MANY,
                    "Equipe",
                    Cardinality.ONE_TO_MANY,
                    attribute("data_inicio", false),
                    attribute("data_fim", false))));

    LogicalModel result = transformer.transform(model);
    Table association = table(result, "Participa");

    assertThat(association.columns().stream().map(column -> column.name()))
        .containsExactly("fk_Atleta_id_atleta", "fk_Equipe_id_equipe", "data_inicio", "data_fim");
    assertThat(association.primaryKeyColumns())
        .containsExactly("fk_Atleta_id_atleta", "fk_Equipe_id_equipe");
    assertThat(association.foreignKeys()).hasSize(2);
    assertThat(association.foreignKeys().stream().map(key -> key.references().table()))
        .containsExactly("Atleta", "Equipe");
  }

  @Test
  void placesOneToManyForeignKeyAndRelationshipAttributesOnManySide() throws Exception {
    ConceptualModel model =
        model(
            List.of(entity("Cliente", "cpf"), entity("Pedido", "id_pedido")),
            List.of(
                relationship(
                    "Realiza",
                    "Cliente",
                    Cardinality.ONE,
                    "Pedido",
                    Cardinality.ZERO_TO_MANY,
                    attribute("canal", false))));

    LogicalModel result = transformer.transform(model);
    Table order = table(result, "Pedido");

    assertThat(order.columns().stream().map(column -> column.name()))
        .contains("fk_Cliente_cpf", "canal");
    assertThat(order.foreignKeys().getFirst().references().table()).isEqualTo("Cliente");
    assertThat(order.foreignKeys().getFirst().localCardinality())
        .isEqualTo(Cardinality.ZERO_TO_MANY);
  }

  @Test
  void choosesMandatoryParticipantForOneToOneAndUsesStableFallback() throws Exception {
    ConceptualModel participation =
        model(
            List.of(entity("A", "id"), entity("B", "id")),
            List.of(relationship("AB", "A", Cardinality.ZERO_TO_ONE, "B", Cardinality.ONE)));
    ConceptualModel tied =
        model(
            List.of(entity("A", "id"), entity("B", "id")),
            List.of(relationship("AB", "A", Cardinality.ONE, "B", Cardinality.ONE)));

    assertThat(table(transformer.transform(participation), "B").foreignKeys()).hasSize(1);
    assertThat(table(transformer.transform(tied), "B").foreignKeys()).hasSize(1);
  }

  @Test
  void expandsCompositeIdentifiersIntoCompositeAssociationKeys() throws Exception {
    Entity first =
        new Entity(
            "First", false, List.of(attribute("tenant", true), attribute("number", true)), null);
    ConceptualModel model =
        model(
            List.of(first, entity("Second", "id")),
            List.of(
                relationship(
                    "Links", "First", Cardinality.ONE_TO_MANY, "Second", Cardinality.ONE_TO_MANY)));

    Table links = table(transformer.transform(model), "Links");

    assertThat(links.primaryKeyColumns())
        .containsExactly("fk_First_tenant", "fk_First_number", "fk_Second_id");
    assertThat(links.foreignKeys().getFirst().columns())
        .containsExactly("fk_First_tenant", "fk_First_number");
  }

  private ConceptualModel model(List<Entity> entities, List<Relationship> relationships) {
    return new ConceptualModel(1, new Diagram("Model"), entities, relationships, List.of());
  }

  private Entity entity(String name, String identifier) {
    return new Entity(name, false, List.of(attribute(identifier, true)), null);
  }

  private Attribute attribute(String name, boolean key) {
    return new Attribute(name, key, false, false, false, false, List.of());
  }

  private Relationship relationship(
      String name,
      String first,
      Cardinality firstCardinality,
      String second,
      Cardinality secondCardinality,
      Attribute... attributes) {
    return new Relationship(
        name,
        false,
        List.of(new Connection(first, firstCardinality), new Connection(second, secondCardinality)),
        Arrays.asList(attributes),
        null);
  }

  private Table table(LogicalModel model, String name) {
    return model.tables().stream()
        .filter(table -> table.name().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
