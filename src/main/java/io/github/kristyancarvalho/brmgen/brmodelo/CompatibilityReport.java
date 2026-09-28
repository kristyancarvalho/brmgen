package io.github.kristyancarvalho.brmgen.brmodelo;

import java.nio.file.Path;
import java.util.List;

public record CompatibilityReport(
    Path jar, String detectedVersion, List<String> missingCapabilities) {
  public CompatibilityReport {
    missingCapabilities = List.copyOf(missingCapabilities);
  }

  public boolean compatible() {
    return missingCapabilities.isEmpty();
  }
}
