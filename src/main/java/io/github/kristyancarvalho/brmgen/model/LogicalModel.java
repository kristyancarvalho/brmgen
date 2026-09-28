package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record LogicalModel(int version, Diagram diagram, List<Table> tables)
    implements ModelDefinition {
  public LogicalModel {
    tables = tables == null ? List.of() : List.copyOf(tables);
  }

  @Override
  public ModelType type() {
    return ModelType.LOGICAL;
  }
}
