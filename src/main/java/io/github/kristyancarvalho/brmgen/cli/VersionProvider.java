package io.github.kristyancarvalho.brmgen.cli;

import picocli.CommandLine.IVersionProvider;

final class VersionProvider implements IVersionProvider {
  @Override
  public String[] getVersion() {
    return new String[] {version()};
  }

  static String version() {
    String value = VersionProvider.class.getPackage().getImplementationVersion();
    return "brmgen " + (value == null ? "development" : value);
  }
}
