package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record Entity(String name, boolean weak, List<Attribute> attributes, Position position) {
  public Entity {
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
  }
}
