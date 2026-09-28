package io.github.kristyancarvalho.brmgen.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ModelType {
  CONCEPTUAL,
  LOGICAL;

  @JsonCreator
  public static ModelType parse(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }

  @JsonValue
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }
}
