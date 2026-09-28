package io.github.kristyancarvalho.brmgen.validation;

import io.github.kristyancarvalho.brmgen.model.Column;
import io.github.kristyancarvalho.brmgen.model.ForeignKey;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LogicalModelValidator {
  public ValidationResult validate(LogicalModel model) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    if (model.version() != 1) {
      add(diagnostics, "L001", "unsupported schema version", "version", "set `version: 1`");
    }
    if (model.diagram() == null || blank(model.diagram().name())) {
      add(diagnostics, "L002", "model name is required", "model.name", "provide a name");
    }
    Map<String, Table> tables = tableIndex(model.tables(), diagnostics);
    for (int index = 0; index < model.tables().size(); index++) {
      validateTable(model.tables().get(index), index, tables, diagnostics);
    }
    return new ValidationResult(diagnostics);
  }

  private Map<String, Table> tableIndex(List<Table> definitions, List<Diagnostic> diagnostics) {
    Map<String, Table> tables = new LinkedHashMap<>();
    for (int index = 0; index < definitions.size(); index++) {
      Table table = definitions.get(index);
      if (blank(table.name())) {
        add(
            diagnostics,
            "L003",
            "table name is required",
            "tables[" + index + "].name",
            "provide a unique table name");
      } else if (tables.putIfAbsent(table.name(), table) != null) {
        add(
            diagnostics,
            "L003",
            "duplicate table `" + table.name() + "`",
            "tables[" + index + "].name",
            "rename or remove the duplicate table");
      }
    }
    return tables;
  }

  private void validateTable(
      Table table, int tableIndex, Map<String, Table> tables, List<Diagnostic> diagnostics) {
    String path = "tables[" + tableIndex + "]";
    Map<String, Column> columns = new LinkedHashMap<>();
    for (int index = 0; index < table.columns().size(); index++) {
      Column column = table.columns().get(index);
      String columnPath = path + ".columns[" + index + "].name";
      if (blank(column.name())) {
        add(diagnostics, "L004", "column name is required", columnPath, "provide a column name");
      } else if (columns.putIfAbsent(column.name(), column) != null) {
        add(
            diagnostics,
            "L004",
            "duplicate column `" + column.name() + "`",
            columnPath,
            "rename or remove the duplicate column");
      }
    }
    if (table.primaryKey() != null && table.primaryKey().columns().isEmpty()) {
      add(
          diagnostics,
          "L005",
          "primary key cannot be empty",
          path + ".primaryKey.columns",
          "list at least one existing column");
    }
    validateExistingColumns(
        table.primaryKeyColumns(),
        columns.keySet(),
        path + ".primaryKey.columns",
        "L005",
        diagnostics);
    validateForeignKeys(table, path, columns, tables, diagnostics);
    validatePosition(table.position(), path + ".position", diagnostics);
  }

  private void validateForeignKeys(
      Table table,
      String path,
      Map<String, Column> columns,
      Map<String, Table> tables,
      List<Diagnostic> diagnostics) {
    Set<String> names = new LinkedHashSet<>();
    for (int index = 0; index < table.foreignKeys().size(); index++) {
      ForeignKey foreignKey = table.foreignKeys().get(index);
      String foreignKeyPath = path + ".foreignKeys[" + index + "]";
      if (!blank(foreignKey.name()) && !names.add(foreignKey.name())) {
        add(
            diagnostics,
            "L006",
            "duplicate foreign key `" + foreignKey.name() + "`",
            foreignKeyPath + ".name",
            "use a unique constraint name");
      }
      if (foreignKey.columns().isEmpty()) {
        add(
            diagnostics,
            "L006",
            "foreign key has no local columns",
            foreignKeyPath + ".columns",
            "list at least one local column");
      }
      validateExistingColumns(
          foreignKey.columns(), columns.keySet(), foreignKeyPath + ".columns", "L006", diagnostics);
      if (foreignKey.references() == null
          || blank(foreignKey.references().table())
          || !tables.containsKey(foreignKey.references().table())) {
        add(
            diagnostics,
            "L007",
            "foreign key references an unknown table",
            foreignKeyPath + ".references.table",
            "reference a declared table");
        continue;
      }
      Table referenced = tables.get(foreignKey.references().table());
      Set<String> referencedColumns = new LinkedHashSet<>();
      referenced.columns().stream().map(Column::name).forEach(referencedColumns::add);
      validateExistingColumns(
          foreignKey.references().columns(),
          referencedColumns,
          foreignKeyPath + ".references.columns",
          "L008",
          diagnostics);
      if (foreignKey.columns().size() != foreignKey.references().columns().size()) {
        add(
            diagnostics,
            "L009",
            "foreign key column counts differ",
            foreignKeyPath,
            "use the same number of local and referenced columns");
      }
      validateCompatibleTypes(foreignKey, columns, referenced, foreignKeyPath, diagnostics);
    }
  }

  private void validateCompatibleTypes(
      ForeignKey foreignKey,
      Map<String, Column> localColumns,
      Table referenced,
      String path,
      List<Diagnostic> diagnostics) {
    Map<String, Column> referencedColumns = new LinkedHashMap<>();
    referenced.columns().forEach(column -> referencedColumns.put(column.name(), column));
    int pairs = Math.min(foreignKey.columns().size(), foreignKey.references().columns().size());
    for (int index = 0; index < pairs; index++) {
      Column local = localColumns.get(foreignKey.columns().get(index));
      Column target = referencedColumns.get(foreignKey.references().columns().get(index));
      if (local != null
          && target != null
          && !blank(local.type())
          && !blank(target.type())
          && !local.type().equalsIgnoreCase(target.type())) {
        add(
            diagnostics,
            "L010",
            "foreign key columns have incompatible types",
            path,
            "use matching local and referenced column types");
      }
    }
  }

  private void validateExistingColumns(
      List<String> selected,
      Set<String> existing,
      String path,
      String code,
      List<Diagnostic> diagnostics) {
    Set<String> unique = new LinkedHashSet<>();
    for (int index = 0; index < selected.size(); index++) {
      String column = selected.get(index);
      if (blank(column) || !existing.contains(column)) {
        add(
            diagnostics,
            code,
            "unknown column `" + display(column) + "`",
            path + "[" + index + "]",
            "reference a column declared in the table");
      } else if (!unique.add(column)) {
        add(
            diagnostics,
            code,
            "duplicate key column `" + column + "`",
            path + "[" + index + "]",
            "remove the duplicate column");
      }
    }
  }

  private void validatePosition(Position position, String path, List<Diagnostic> diagnostics) {
    if (position != null
        && (position.x() == null || position.y() == null || position.x() < 0 || position.y() < 0)) {
      add(
          diagnostics,
          "L011",
          "invalid table position",
          path,
          "provide non-negative integer x and y coordinates");
    }
  }

  private void add(
      List<Diagnostic> diagnostics, String code, String message, String field, String suggestion) {
    diagnostics.add(new Diagnostic(code, message, field, message, suggestion));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String display(String value) {
    return value == null ? "null" : value;
  }
}
