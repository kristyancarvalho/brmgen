package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record ModelDefinition(
    int version,
    Diagram diagram,
    List<Entity> entities,
    List<Relationship> relationships,
    List<Generalization> generalizations) {
  public ModelDefinition {
    entities = entities == null ? List.of() : List.copyOf(entities);
    relationships = relationships == null ? List.of() : List.copyOf(relationships);
    generalizations = generalizations == null ? List.of() : List.copyOf(generalizations);
  }
}
