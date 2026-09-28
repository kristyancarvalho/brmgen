package io.github.kristyancarvalho.brmgen.model;

import java.util.List;

public record Generalization(
    String parent, List<String> children, boolean total, boolean disjoint, Position position) {
  public Generalization {
    children = children == null ? List.of() : List.copyOf(children);
  }
}
