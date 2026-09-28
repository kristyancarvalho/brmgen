package io.github.kristyancarvalho.brmgen.layout;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Table;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicalLayoutEngineTest {
  @Test
  void placesTablesDeterministicallyAndPreservesManualPositions() {
    Position manual = new Position(900, 400);
    LogicalModel model =
        new LogicalModel(
            1,
            new Diagram("Layout"),
            List.of(table("First", null), table("Manual", manual), table("Third", null)));

    LogicalModel first = new LogicalLayoutEngine().layout(model);
    LogicalModel second = new LogicalLayoutEngine().layout(model);

    assertThat(first).isEqualTo(second);
    assertThat(first.tables().get(0).position()).isEqualTo(new Position(60, 60));
    assertThat(first.tables().get(1).position()).isEqualTo(manual);
    assertThat(first.tables().get(2).position()).isEqualTo(new Position(360, 60));
  }

  private Table table(String name, Position position) {
    return new Table(name, List.of(), null, List.of(), position);
  }
}
