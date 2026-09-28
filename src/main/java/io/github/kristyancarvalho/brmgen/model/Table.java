package io.github.kristyancarvalho.brmgen.model;

import java.util.LinkedHashSet;
import java.util.List;

public record Table(
    String name,
    List<Column> columns,
    PrimaryKey primaryKey,
    List<ForeignKey> foreignKeys,
    Position position) {
  public Table {
    columns = columns == null ? List.of() : List.copyOf(columns);
    foreignKeys = foreignKeys == null ? List.of() : List.copyOf(foreignKeys);
  }

  public List<String> primaryKeyColumns() {
    if (primaryKey != null && !primaryKey.columns().isEmpty()) {
      return primaryKey.columns();
    }
    LinkedHashSet<String> names = new LinkedHashSet<>();
    columns.stream().filter(Column::primaryKey).map(Column::name).forEach(names::add);
    return List.copyOf(names);
  }
}
