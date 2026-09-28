package io.github.kristyancarvalho.brmgen.model;

public sealed interface ModelDefinition permits ConceptualModel, LogicalModel {
  int version();

  Diagram diagram();

  ModelType type();
}
