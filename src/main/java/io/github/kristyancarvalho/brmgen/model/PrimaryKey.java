package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record PrimaryKey(List<String> columns) {
  public PrimaryKey {
    columns = columns == null ? List.of() : List.copyOf(columns);
  }
}
