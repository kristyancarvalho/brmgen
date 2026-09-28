package io.github.kristyancarvalho.brmgen.brmodelo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.Position;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeBrm3WriterTest {
  @Test
  void rejectsDerivedAttributesUnsupportedByBrmodelo() {
    Attribute attribute = new Attribute("age", false, false, false, true, false, List.of());
    ModelDefinition model =
        new ModelDefinition(
            1,
            new Diagram("Unsupported"),
            List.of(new Entity("Customer", false, List.of(attribute), new Position(80, 80))),
            List.of(),
            List.of());

    assertThatThrownBy(
            () ->
                new NativeBrm3Writer().write(model, Path.of("unused.jar"), Path.of("unused.brM3")))
        .isInstanceOf(BrmodeloException.class)
        .hasMessageContaining("error[E208]");
  }
}
