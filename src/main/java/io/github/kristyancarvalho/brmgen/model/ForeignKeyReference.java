package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record ForeignKeyReference(String table, List<String> columns) {
  public ForeignKeyReference {
    columns = columns == null ? List.of() : List.copyOf(columns);
  }
}
