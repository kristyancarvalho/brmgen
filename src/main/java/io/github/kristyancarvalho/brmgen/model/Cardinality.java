package io.github.kristyancarvalho.brmgen.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum Cardinality {
  ZERO_TO_ONE("0..1"),
  ONE("1..1"),
  ONE_TO_MANY("1..n"),
  ZERO_TO_MANY("0..n");

  private final String value;

  Cardinality(String value) {
    this.value = value;
  }

  @JsonCreator
  public static Cardinality parse(String value) {
    if ("1".equals(value)) {
      return ONE;
    }
    return Arrays.stream(values())
        .filter(cardinality -> cardinality.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unsupported cardinality `" + value + "`"));
  }

  @JsonValue
  public String value() {
    return value;
  }

  public String nativeName() {
    return switch (this) {
      case ZERO_TO_ONE -> "C01";
      case ONE -> "C11";
      case ONE_TO_MANY -> "C1N";
      case ZERO_TO_MANY -> "C0N";
    };
  }

  public int minimum() {
    return this == ZERO_TO_ONE || this == ZERO_TO_MANY ? 0 : 1;
  }

  public boolean many() {
    return this == ONE_TO_MANY || this == ZERO_TO_MANY;
  }
}
