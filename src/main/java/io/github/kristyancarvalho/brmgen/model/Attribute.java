package io.github.kristyancarvalho.brmgen.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record Attribute(
    String name,
    boolean key,
    boolean partialKey,
    boolean multivalued,
    boolean derived,
    @JsonAlias("attributes") List<Attribute> components) {
  public Attribute {
    components = components == null ? List.of() : List.copyOf(components);
  }
}
