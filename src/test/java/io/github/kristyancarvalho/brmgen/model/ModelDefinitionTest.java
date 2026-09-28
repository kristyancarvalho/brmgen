package io.github.kristyancarvalho.brmgen.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConceptualModelTest {
  @Test
  void replacesMissingCollectionsWithEmptyLists() {
    ConceptualModel model = new ConceptualModel(1, new Diagram("Empty"), null, null, null);

    assertThat(model.entities()).isEmpty();
    assertThat(model.relationships()).isEmpty();
    assertThat(model.generalizations()).isEmpty();
  }

  @Test
  void makesCollectionsImmutableCopies() {
    ArrayList<Entity> entities = new ArrayList<>();
    ConceptualModel model =
        new ConceptualModel(1, new Diagram("Stable"), entities, List.of(), List.of());
    entities.add(new Entity("Late", false, List.of(), null));

    assertThat(model.entities()).isEmpty();
    assertThatThrownBy(() -> model.entities().add(new Entity("Other", false, List.of(), null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
