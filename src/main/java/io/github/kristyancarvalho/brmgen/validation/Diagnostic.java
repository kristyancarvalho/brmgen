package io.github.kristyancarvalho.brmgen.validation;

public record Diagnostic(
    String code, String message, String field, String cause, String suggestion) {
  public String render() {
    StringBuilder output = new StringBuilder("error[").append(code).append("]: ").append(message);
    if (field != null && !field.isBlank()) {
      output.append(System.lineSeparator()).append("at: ").append(field);
    }
    if (cause != null && !cause.isBlank()) {
      output.append(System.lineSeparator()).append("cause: ").append(cause);
    }
    if (suggestion != null && !suggestion.isBlank()) {
      output.append(System.lineSeparator()).append("suggestion: ").append(suggestion);
    }
    return output.toString();
  }
}
