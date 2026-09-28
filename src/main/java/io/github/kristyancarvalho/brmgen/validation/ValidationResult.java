package io.github.kristyancarvalho.brmgen.validation;

import java.util.List;

public record ValidationResult(List<Diagnostic> diagnostics) {
  public ValidationResult {
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean isValid() {
    return diagnostics.isEmpty();
  }
}
