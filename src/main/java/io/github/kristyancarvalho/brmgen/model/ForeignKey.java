package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record ForeignKey(
    String name,
    List<String> columns,
    ForeignKeyReference references,
    Cardinality localCardinality,
    Cardinality referencedCardinality) {
  public ForeignKey {
    columns = columns == null ? List.of() : List.copyOf(columns);
    localCardinality = localCardinality == null ? Cardinality.ONE_TO_MANY : localCardinality;
    referencedCardinality = referencedCardinality == null ? Cardinality.ONE : referencedCardinality;
  }
}
