package io.github.kristyancarvalho.brmgen.parser;

public final class ModelParseException extends Exception {
  public ModelParseException(String message) {
    super(message);
  }

  public ModelParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
