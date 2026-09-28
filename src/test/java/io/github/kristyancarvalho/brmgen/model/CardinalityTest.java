package io.github.kristyancarvalho.brmgen.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CardinalityTest {
  @ParameterizedTest
  @CsvSource({"0..1,ZERO_TO_ONE", "1,ONE", "1..n,ONE_TO_MANY", "0..n,ZERO_TO_MANY"})
  void parsesSupportedValues(String input, Cardinality expected) {
    assertThat(Cardinality.parse(input)).isEqualTo(expected);
    assertThat(expected.value()).isEqualTo(input);
  }

  @ParameterizedTest
  @CsvSource({"0..2", "many", "1..N"})
  void rejectsUnsupportedValues(String input) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Cardinality.parse(input))
        .withMessageContaining(input);
  }
}
