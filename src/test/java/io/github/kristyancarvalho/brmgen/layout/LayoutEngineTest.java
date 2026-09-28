package io.github.kristyancarvalho.brmgen.layout;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.Generalization;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutEngineTest {
  private final LayoutEngine layout = new LayoutEngine();

  @Test
  void preservesManualPositionsAndPlacesMissingEntities() {
    Entity manual = new Entity("Manual", false, List.of(), new Position(80, 80));
    Entity automatic = new Entity("Automatic", false, List.of(), null);
    ModelDefinition model = model(List.of(manual, automatic), List.of(), List.of());

    ModelDefinition result = layout.layout(model);

    assertThat(result.entities().get(0).position()).isEqualTo(new Position(80, 80));
    assertThat(result.entities().get(1).position()).isEqualTo(new Position(360, 80));
  }

  @Test
  void producesDeterministicNonOverlappingEntityPositions() {
    ModelDefinition model =
        model(
            List.of(entity("A"), entity("B"), entity("C"), entity("D"), entity("E")),
            List.of(),
            List.of());

    ModelDefinition first = layout.layout(model);
    ModelDefinition second = layout.layout(model);

    assertThat(first).isEqualTo(second);
    assertThat(first.entities().stream().map(Entity::position)).doesNotHaveDuplicates();
    assertThat(layout.layout(first)).isEqualTo(first);
  }

  @Test
  void placesRelationshipsRelativeToParticipantsWithoutOverlappingThem() {
    Entity left = new Entity("Left", false, List.of(), new Position(100, 100));
    Entity right = new Entity("Right", false, List.of(), new Position(500, 100));
    Relationship relationship =
        new Relationship(
            "Connects",
            false,
            List.of(
                new Connection("Left", Cardinality.ONE),
                new Connection("Right", Cardinality.ZERO_TO_MANY)),
            List.of(),
            null);

    ModelDefinition result =
        layout.layout(model(List.of(left, right), List.of(relationship), List.of()));

    assertThat(result.relationships().getFirst().position()).isEqualTo(new Position(300, 100));
  }

  @Test
  void placesGeneralizationsRelativeToTheirEntities() {
    Entity parent = new Entity("Parent", false, List.of(), new Position(100, 100));
    Entity child = new Entity("Child", false, List.of(), new Position(500, 300));
    Generalization generalization =
        new Generalization("Parent", List.of("Child"), true, true, null);

    ModelDefinition result =
        layout.layout(model(List.of(parent, child), List.of(), List.of(generalization)));

    assertThat(result.generalizations().getFirst().position()).isEqualTo(new Position(300, 200));
  }

  @Test
  void preservesManualRelationshipAndGeneralizationPositions() {
    Position relationshipPosition = new Position(700, 200);
    Position generalizationPosition = new Position(700, 400);
    Relationship relationship =
        new Relationship("Manual", false, List.of(), List.of(), relationshipPosition);
    Generalization generalization =
        new Generalization("A", List.of("B"), false, false, generalizationPosition);

    ModelDefinition result =
        layout.layout(
            model(
                List.of(entity("A"), entity("B")), List.of(relationship), List.of(generalization)));

    assertThat(result.relationships().getFirst().position()).isEqualTo(relationshipPosition);
    assertThat(result.generalizations().getFirst().position()).isEqualTo(generalizationPosition);
  }

  private Entity entity(String name) {
    return new Entity(name, false, List.of(), null);
  }

  private ModelDefinition model(
      List<Entity> entities,
      List<Relationship> relationships,
      List<Generalization> generalizations) {
    return new ModelDefinition(1, new Diagram("Layout"), entities, relationships, generalizations);
  }
}
