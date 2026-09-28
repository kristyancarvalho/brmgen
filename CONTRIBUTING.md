# Contributing to brmgen

## Requirements

- Git
- A Java runtime supported by the checked-in Gradle Wrapper
- Network access for initial dependency and Java 21 toolchain resolution
- GitHub CLI when maintaining issues from the terminal
- A locally obtained compatible brModelo JAR only for native integration work

The brModelo JAR must never be committed, vendored, or redistributed from this repository.

## Local Setup

Fork or clone the repository, then verify the build:

```bash
./gradlew check
./gradlew build
```

Useful focused commands are:

```bash
./gradlew test
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew run --args='version'
BRMODELO_JAR=/path/to/brModelo.jar ./gradlew integrationTest
```

## Development Workflow

Development starts with a focused GitHub issue. Long-lived work integrates through `dev`; `main` contains released or release-ready milestone history.

Create one of these branches from an up-to-date `dev` branch:

```text
feature/<issue>-<slug>
fix/<issue>-<slug>
chore/<issue>-<slug>
```

Keep work within the issue scope. Open a separate issue when newly discovered work is independent and non-blocking. Feature branches merge into `dev`; `dev` reaches `main` only after its milestone is complete and validated.

## Commits and Pull Requests

Use Conventional Commits in the form `type(scope): summary`, such as:

```text
feat(parser): add yaml model support
fix(validation): reject unknown entity references
test(layout): cover explicit coordinate precedence
```

Pull requests should link the issue, explain user-visible behavior, identify compatibility or security effects, and list the checks run. Avoid unrelated refactoring. Update public documentation whenever commands, supported input, setup, or behavior change.

## Definition of Done

A change is complete when its acceptance criteria are met, relevant tests and regression coverage pass, `./gradlew check` and `./gradlew build` succeed, diagnostics are suitable for users, documentation is accurate, and no external binary, secret, generated output, or explanatory source comment was added.

Changes involving native generation must also consider the brModelo compatibility boundary. Integration tests must use an explicitly supplied local JAR and remain separate from the normal test suite.
