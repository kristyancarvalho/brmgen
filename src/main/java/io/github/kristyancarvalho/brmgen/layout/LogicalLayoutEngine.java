package io.github.kristyancarvalho.brmgen.layout;

import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Table;
import java.util.ArrayList;
import java.util.List;

public final class LogicalLayoutEngine {
  private static final int START_X = 60;
  private static final int START_Y = 60;
  private static final int COLUMN_GAP = 300;
  private static final int ROW_GAP = 240;

  public LogicalModel layout(LogicalModel model) {
    List<Table> tables = new ArrayList<>();
    int automaticIndex = 0;
    for (Table table : model.tables()) {
      Position position = table.position();
      if (position == null) {
        position =
            new Position(
                START_X + automaticIndex % 3 * COLUMN_GAP, START_Y + automaticIndex / 3 * ROW_GAP);
        automaticIndex++;
      }
      tables.add(
          new Table(
              table.name(), table.columns(), table.primaryKey(), table.foreignKeys(), position));
    }
    return new LogicalModel(model.version(), model.diagram(), tables);
  }
}
