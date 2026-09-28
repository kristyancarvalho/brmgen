package io.github.kristyancarvalho.brmgen;

import io.github.kristyancarvalho.brmgen.cli.RootCommand;
import picocli.CommandLine;

public final class Brmgen {
  private Brmgen() {}

  public static void main(String[] args) {
    int exitCode = new CommandLine(new RootCommand()).execute(args);
    System.exit(exitCode);
  }
}
