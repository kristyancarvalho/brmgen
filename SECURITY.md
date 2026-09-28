# Security Policy

## Supported Versions

`brmgen` has not published a stable release. Security fixes are applied to the latest development line until release-specific support is announced.

## Reporting a Vulnerability

Use GitHub private vulnerability reporting or a private Security Advisory for this repository when available. Include the affected revision, environment, impact, reproduction steps, and the smallest safe sample needed to investigate.

Do not open a public issue or disclose exploitable details before a fix or coordinated disclosure is ready. The maintainers will acknowledge and assess reports as availability permits, but this project does not promise a fixed response or remediation time.

## Security Scope

YAML and JSON model definitions, paths, `.brM3` files, and third-party JARs are untrusted inputs. Reports involving unintended file access or overwrite, unsafe parsing, arbitrary class loading, command execution, or unsafe Java deserialization are in scope.

`brmgen` does not download or redistribute brModelo. Supply a brModelo JAR only from a source you trust and select it explicitly. A JAR executes code with the permissions of the current process.

Java native serialization is a sensitive boundary. Do not load unknown or untrusted `.brM3` files. Future reading or round-trip verification must restrict expected classes and object graphs before it is suitable for untrusted files.
