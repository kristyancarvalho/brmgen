package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record Relationship(
    String name,
    boolean identifying,
    List<Connection> connections,
    List<Attribute> attributes,
    Position position) {
  public Relationship {
    connections = connections == null ? List.of() : List.copyOf(connections);
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
  }
}
