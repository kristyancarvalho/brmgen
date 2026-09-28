package io.github.kristyancarvalho.brmgen.transform;

import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.Column;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.ForeignKey;
import io.github.kristyancarvalho.brmgen.model.ForeignKeyReference;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.PrimaryKey;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import io.github.kristyancarvalho.brmgen.model.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LogicalTransformer {
  public LogicalModel transform(ConceptualModel model) throws TransformationException {
    ensureSupported(model);
    Map<String, TableBuilder> tables = new LinkedHashMap<>();
    for (Entity entity : model.entities()) {
      tables.put(entity.name(), entityTable(entity));
    }
    for (Relationship relationship : model.relationships()) {
      transformRelationship(relationship, tables);
    }
    return new LogicalModel(
        model.version(),
        model.diagram(),
        tables.values().stream().map(TableBuilder::build).toList());
  }

  private TableBuilder entityTable(Entity entity) throws TransformationException {
    TableBuilder table = new TableBuilder(entity.name(), entity.position());
    for (Attribute attribute : entity.attributes()) {
      addAttribute(table, attribute, "", attribute.key());
    }
    return table;
  }

  private void addAttribute(
      TableBuilder table, Attribute attribute, String prefix, boolean inheritedKey)
      throws TransformationException {
    if (attribute.derived()) {
      return;
    }
    if (attribute.multivalued()) {
      throw unsupported("multivalued attribute `" + attribute.name() + "`");
    }
    String name = prefix + attribute.name();
    boolean key = inheritedKey || attribute.key() || attribute.partialKey();
    if (attribute.components().isEmpty()) {
      table.addColumn(new Column(name, "", key, false));
      if (key) {
        table.addPrimaryKey(name);
      }
      return;
    }
    for (Attribute component : attribute.components()) {
      addAttribute(table, component, name + "_", key);
    }
  }

  private void transformRelationship(Relationship relationship, Map<String, TableBuilder> tables)
      throws TransformationException {
    if (relationship.connections().size() != 2) {
      throw unsupported("non-binary relationship `" + relationship.name() + "`");
    }
    Connection first = relationship.connections().get(0);
    Connection second = relationship.connections().get(1);
    if (first.cardinality().many() && second.cardinality().many()) {
      createAssociativeTable(relationship, first, second, tables);
      return;
    }
    if (first.cardinality().many() != second.cardinality().many()) {
      Connection local = first.cardinality().many() ? first : second;
      Connection referenced = local == first ? second : first;
      addForeignKey(relationship, local, referenced, tables);
      return;
    }
    Connection local = oneToOneLocal(first, second);
    Connection referenced = local == first ? second : first;
    addForeignKey(relationship, local, referenced, tables);
  }

  private Connection oneToOneLocal(Connection first, Connection second) {
    if (first.cardinality().minimum() != second.cardinality().minimum()) {
      return first.cardinality().minimum() == 1 ? first : second;
    }
    return second;
  }

  private void addForeignKey(
      Relationship relationship,
      Connection local,
      Connection referenced,
      Map<String, TableBuilder> tables)
      throws TransformationException {
    TableBuilder localTable = tables.get(local.entity());
    TableBuilder referencedTable = tables.get(referenced.entity());
    List<String> referencedKeys = requirePrimaryKey(referencedTable);
    List<String> localColumns = new ArrayList<>();
    for (String referencedKey : referencedKeys) {
      String localName = "fk_" + referenced.entity() + "_" + referencedKey;
      localTable.addColumn(
          new Column(localName, referencedTable.typeOf(referencedKey), false, false));
      localColumns.add(localName);
    }
    localTable.addForeignKey(
        new ForeignKey(
            "fk_" + local.entity() + "_" + referenced.entity(),
            localColumns,
            new ForeignKeyReference(referenced.entity(), referencedKeys),
            local.cardinality(),
            referenced.cardinality()));
    for (Attribute attribute : relationship.attributes()) {
      addAttribute(localTable, attribute, "", false);
    }
  }

  private void createAssociativeTable(
      Relationship relationship,
      Connection first,
      Connection second,
      Map<String, TableBuilder> tables)
      throws TransformationException {
    if (tables.containsKey(relationship.name())) {
      throw new TransformationException(
          "error[T002]: relationship `"
              + relationship.name()
              + "` conflicts with an existing table name");
    }
    TableBuilder association = new TableBuilder(relationship.name(), relationship.position());
    addAssociationReference(association, first, tables.get(first.entity()));
    addAssociationReference(association, second, tables.get(second.entity()));
    for (Attribute attribute : relationship.attributes()) {
      addAttribute(association, attribute, "", false);
    }
    tables.put(relationship.name(), association);
  }

  private void addAssociationReference(
      TableBuilder association, Connection connection, TableBuilder referenced)
      throws TransformationException {
    List<String> referencedKeys = requirePrimaryKey(referenced);
    List<String> localColumns = new ArrayList<>();
    for (String referencedKey : referencedKeys) {
      String localName = "fk_" + connection.entity() + "_" + referencedKey;
      association.addColumn(new Column(localName, referenced.typeOf(referencedKey), true, false));
      association.addPrimaryKey(localName);
      localColumns.add(localName);
    }
    association.addForeignKey(
        new ForeignKey(
            "fk_" + association.name() + "_" + connection.entity(),
            localColumns,
            new ForeignKeyReference(connection.entity(), referencedKeys),
            connection.cardinality(),
            Cardinality.ONE));
  }

  private List<String> requirePrimaryKey(TableBuilder table) throws TransformationException {
    if (table.primaryKey().isEmpty()) {
      throw new TransformationException(
          "error[T003]: entity `" + table.name() + "` has no identifier for a foreign key");
    }
    return List.copyOf(table.primaryKey());
  }

  private void ensureSupported(ConceptualModel model) throws TransformationException {
    if (!model.generalizations().isEmpty()) {
      throw unsupported("generalization transformation");
    }
    if (model.entities().stream().anyMatch(Entity::weak)
        || model.relationships().stream().anyMatch(Relationship::identifying)) {
      throw unsupported("weak or identifying transformation");
    }
  }

  private TransformationException unsupported(String construct) {
    return new TransformationException(
        "error[T001]: conceptual-to-logical transformation does not yet support " + construct);
  }

  private static final class TableBuilder {
    private final String name;
    private final io.github.kristyancarvalho.brmgen.model.Position position;
    private final Map<String, Column> columns = new LinkedHashMap<>();
    private final List<String> primaryKey = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();

    private TableBuilder(String name, io.github.kristyancarvalho.brmgen.model.Position position) {
      this.name = name;
      this.position = position;
    }

    private String name() {
      return name;
    }

    private List<String> primaryKey() {
      return primaryKey;
    }

    private void addColumn(Column column) throws TransformationException {
      if (columns.putIfAbsent(column.name(), column) != null) {
        throw new TransformationException(
            "error[T004]: duplicate transformed column `"
                + column.name()
                + "` in table `"
                + name
                + "`");
      }
    }

    private void addPrimaryKey(String column) {
      primaryKey.add(column);
    }

    private void addForeignKey(ForeignKey foreignKey) {
      foreignKeys.add(foreignKey);
    }

    private String typeOf(String column) {
      return columns.get(column).type();
    }

    private Table build() {
      PrimaryKey key = primaryKey.isEmpty() ? null : new PrimaryKey(primaryKey);
      return new Table(name, List.copyOf(columns.values()), key, foreignKeys, position);
    }
  }
}
