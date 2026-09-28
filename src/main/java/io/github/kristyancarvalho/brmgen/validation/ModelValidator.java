package io.github.kristyancarvalho.brmgen.validation;

import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.Generalization;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelValidator {
  public ValidationResult validate(ModelDefinition model) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    validateVersion(model, diagnostics);
    validateDiagram(model, diagnostics);

    Map<String, Entity> entities = validateEntities(model.entities(), diagnostics);
    validateRelationships(model.relationships(), entities, diagnostics);
    validateWeakEntities(model.entities(), model.relationships(), diagnostics);
    validateGeneralizations(model.generalizations(), entities.keySet(), diagnostics);
    return new ValidationResult(diagnostics);
  }

  private void validateVersion(ModelDefinition model, List<Diagnostic> diagnostics) {
    if (model.version() != 1) {
      add(
          diagnostics,
          "E001",
          "unsupported schema version `" + model.version() + "`",
          "version",
          "brmgen currently supports schema version 1",
          "set `version: 1`");
    }
  }

  private void validateDiagram(ModelDefinition model, List<Diagnostic> diagnostics) {
    if (model.diagram() == null || blank(model.diagram().name())) {
      add(
          diagnostics,
          "E002",
          "diagram name is required",
          "diagram.name",
          "the diagram must have a non-blank name",
          "set `diagram.name` to a descriptive value");
    }
  }

  private Map<String, Entity> validateEntities(
      List<Entity> entities, List<Diagnostic> diagnostics) {
    Map<String, Entity> byName = new LinkedHashMap<>();
    for (int index = 0; index < entities.size(); index++) {
      Entity entity = entities.get(index);
      String path = "entities[" + index + "]";
      if (blank(entity.name())) {
        add(
            diagnostics,
            "E010",
            "entity name is required",
            path + ".name",
            "entity names cannot be blank",
            "provide a unique entity name");
      } else if (byName.putIfAbsent(entity.name(), entity) != null) {
        add(
            diagnostics,
            "E003",
            "duplicate entity `" + entity.name() + "`",
            path + ".name",
            "entity names must be unique",
            "rename or remove one of the duplicate entities");
      }
      validateAttributes(
          entity.attributes(), path + ".attributes", entity.weak(), true, diagnostics);
      validatePosition(entity.position(), path + ".position", diagnostics);
    }
    return byName;
  }

  private void validateRelationships(
      List<Relationship> relationships,
      Map<String, Entity> entities,
      List<Diagnostic> diagnostics) {
    Set<String> names = new LinkedHashSet<>();
    for (int index = 0; index < relationships.size(); index++) {
      Relationship relationship = relationships.get(index);
      String path = "relationships[" + index + "]";
      if (blank(relationship.name())) {
        add(
            diagnostics,
            "E010",
            "relationship name is required",
            path + ".name",
            "relationship names cannot be blank",
            "provide a unique relationship name");
      } else if (!names.add(relationship.name())) {
        add(
            diagnostics,
            "E004",
            "duplicate relationship `" + relationship.name() + "`",
            path + ".name",
            "relationship names must be unique",
            "rename or remove one of the duplicate relationships");
      }
      if (relationship.connections().size() < 2) {
        add(
            diagnostics,
            "E006",
            "relationship `" + display(relationship.name()) + "` has fewer than two participants",
            path + ".connections",
            "a relationship requires at least two connections",
            "add the missing entity connection");
      }
      validateConnections(relationship, path, entities, diagnostics);
      validateAttributes(
          relationship.attributes(), path + ".attributes", false, false, diagnostics);
      validatePosition(relationship.position(), path + ".position", diagnostics);
      if (relationship.identifying()
          && relationship.connections().stream()
              .map(Connection::entity)
              .map(entities::get)
              .noneMatch(entity -> entity != null && entity.weak())) {
        add(
            diagnostics,
            "E012",
            "identifying relationship `" + display(relationship.name()) + "` has no weak entity",
            path + ".identifying",
            "identifying relationships must identify at least one weak entity",
            "connect a weak entity or remove `identifying: true`");
      }
    }
  }

  private void validateConnections(
      Relationship relationship,
      String path,
      Map<String, Entity> entities,
      List<Diagnostic> diagnostics) {
    for (int index = 0; index < relationship.connections().size(); index++) {
      Connection connection = relationship.connections().get(index);
      String connectionPath = path + ".connections[" + index + "]";
      if (blank(connection.entity()) || !entities.containsKey(connection.entity())) {
        add(
            diagnostics,
            "E005",
            "unknown entity `" + display(connection.entity()) + "`",
            connectionPath + ".entity",
            "relationship `"
                + display(relationship.name())
                + "` references an entity that is not declared",
            "declare the entity or correct the reference");
      }
      if (connection.cardinality() == null) {
        add(
            diagnostics,
            "E007",
            "connection cardinality is required",
            connectionPath + ".cardinality",
            "every relationship connection needs an official brModelo cardinality",
            "use one of: 0..1, 1..1, 1..n, 0..n");
      }
    }
  }

  private void validateAttributes(
      List<Attribute> attributes,
      String path,
      boolean weakOwner,
      boolean entityOwner,
      List<Diagnostic> diagnostics) {
    Set<String> names = new LinkedHashSet<>();
    for (int index = 0; index < attributes.size(); index++) {
      Attribute attribute = attributes.get(index);
      String attributePath = path + "[" + index + "]";
      if (blank(attribute.name())) {
        add(
            diagnostics,
            "E010",
            "attribute name is required",
            attributePath + ".name",
            "attribute names cannot be blank",
            "provide a name unique within its owner");
      } else if (!names.add(attribute.name())) {
        add(
            diagnostics,
            "E008",
            "duplicate attribute `" + attribute.name() + "`",
            attributePath + ".name",
            "attribute names must be unique within the same owner",
            "rename or remove the duplicate attribute");
      }
      if (attribute.composite() && attribute.components().isEmpty()) {
        add(
            diagnostics,
            "E009",
            "composite attribute `" + display(attribute.name()) + "` has no components",
            attributePath + ".components",
            "a composite attribute must contain at least one component",
            "add a component or remove `composite: true`");
      }
      if (attribute.key() && attribute.partialKey()) {
        contradictory(attribute, attributePath, "key and partialKey", diagnostics);
      }
      if (attribute.derived() && (attribute.key() || attribute.partialKey())) {
        contradictory(attribute, attributePath, "derived and key flags", diagnostics);
      }
      if (attribute.partialKey() && (!entityOwner || !weakOwner)) {
        add(
            diagnostics,
            "E011",
            "partial key `" + display(attribute.name()) + "` does not belong to a weak entity",
            attributePath + ".partialKey",
            "partial keys are only valid on weak entities",
            "mark the owning entity as weak or remove `partialKey: true`");
      }
      validateAttributes(
          attribute.components(),
          attributePath + ".components",
          weakOwner,
          entityOwner,
          diagnostics);
    }
  }

  private void contradictory(
      Attribute attribute, String path, String flags, List<Diagnostic> diagnostics) {
    add(
        diagnostics,
        "E011",
        "attribute `" + display(attribute.name()) + "` has contradictory flags",
        path,
        flags + " cannot be combined",
        "keep only the flag that represents the attribute semantics");
  }

  private void validateWeakEntities(
      List<Entity> entities, List<Relationship> relationships, List<Diagnostic> diagnostics) {
    Set<String> identified = new HashSet<>();
    relationships.stream()
        .filter(Relationship::identifying)
        .flatMap(relationship -> relationship.connections().stream())
        .map(Connection::entity)
        .forEach(identified::add);
    for (int index = 0; index < entities.size(); index++) {
      Entity entity = entities.get(index);
      if (!entity.weak()) {
        continue;
      }
      if (entity.attributes().stream().noneMatch(Attribute::partialKey)) {
        add(
            diagnostics,
            "E012",
            "weak entity `" + display(entity.name()) + "` has no partial key",
            "entities[" + index + "].attributes",
            "a weak entity requires a partial-key attribute",
            "mark one attribute with `partialKey: true`");
      }
      if (!identified.contains(entity.name())) {
        add(
            diagnostics,
            "E012",
            "weak entity `" + display(entity.name()) + "` is not identified",
            "entities[" + index + "].weak",
            "no identifying relationship connects this weak entity",
            "add an identifying relationship involving the entity");
      }
    }
  }

  private void validateGeneralizations(
      List<Generalization> generalizations, Set<String> entities, List<Diagnostic> diagnostics) {
    Map<String, Set<String>> graph = new LinkedHashMap<>();
    for (int index = 0; index < generalizations.size(); index++) {
      Generalization generalization = generalizations.get(index);
      String path = "generalizations[" + index + "]";
      validateEntityReference(generalization.parent(), path + ".parent", entities, diagnostics);
      if (generalization.children().isEmpty()) {
        add(
            diagnostics,
            "E013",
            "generalization has no children",
            path + ".children",
            "a generalization requires at least one specialized entity",
            "add at least one child entity");
      }
      Set<String> children = new LinkedHashSet<>();
      for (int childIndex = 0; childIndex < generalization.children().size(); childIndex++) {
        String child = generalization.children().get(childIndex);
        validateEntityReference(
            child, path + ".children[" + childIndex + "]", entities, diagnostics);
        if (!children.add(child)) {
          add(
              diagnostics,
              "E013",
              "duplicate generalization child `" + display(child) + "`",
              path + ".children[" + childIndex + "]",
              "a child can appear only once in one generalization",
              "remove the duplicate child");
        }
      }
      graph
          .computeIfAbsent(generalization.parent(), ignored -> new LinkedHashSet<>())
          .addAll(children);
      validatePosition(generalization.position(), path + ".position", diagnostics);
    }
    for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
      for (String child : entry.getValue()) {
        if (entry.getKey() != null && reaches(child, entry.getKey(), graph, new HashSet<>())) {
          add(
              diagnostics,
              "E014",
              "cyclic generalization involving `" + entry.getKey() + "` and `" + child + "`",
              "generalizations",
              "an entity cannot specialize itself directly or transitively",
              "remove a parent-child edge from the cycle");
        }
      }
    }
  }

  private void validateEntityReference(
      String name, String path, Set<String> entities, List<Diagnostic> diagnostics) {
    if (blank(name) || !entities.contains(name)) {
      add(
          diagnostics,
          "E013",
          "unknown generalization entity `" + display(name) + "`",
          path,
          "the generalization references an entity that is not declared",
          "declare the entity or correct the reference");
    }
  }

  private boolean reaches(
      String current, String target, Map<String, Set<String>> graph, Set<String> visited) {
    if (target.equals(current)) {
      return true;
    }
    if (current == null || !visited.add(current)) {
      return false;
    }
    return graph.getOrDefault(current, Set.of()).stream()
        .anyMatch(child -> reaches(child, target, graph, visited));
  }

  private void validatePosition(Position position, String path, List<Diagnostic> diagnostics) {
    if (position == null) {
      return;
    }
    if (position.x() == null || position.y() == null || position.x() < 0 || position.y() < 0) {
      add(
          diagnostics,
          "E015",
          "invalid position",
          path,
          "both coordinates must be non-negative integers",
          "provide `x` and `y` values greater than or equal to zero");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String display(String value) {
    return blank(value) ? "<missing>" : value;
  }

  private void add(
      List<Diagnostic> diagnostics,
      String code,
      String message,
      String field,
      String cause,
      String suggestion) {
    diagnostics.add(new Diagnostic(code, message, field, cause, suggestion));
  }
}
