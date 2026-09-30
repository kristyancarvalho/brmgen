.PHONY: help build test install check clean gtk-build gtk-install gtk-run gtk-test

# Default target
help:
	@echo "brmgen - Available targets:"
	@echo ""
	@echo "Java project:"
	@echo "  build         - Build the Java CLI (./gradlew build)"
	@echo "  install       - Install distribution (./gradlew installDist)"
	@echo "  test          - Run unit tests (./gradlew test)"
	@echo "  integration   - Run integration tests (BRMODELO_JAR=... ./gradlew integrationTest)"
	@echo "  check         - Run all checks (./gradlew check)"
	@echo "  clean         - Clean build artifacts (./gradlew clean)"
	@echo ""
	@echo "GTK interface (Python):"
	@echo "  gtk-build     - Build/install GTK package in development mode"
	@echo "  gtk-install   - Install GTK package with dependencies"
	@echo "  gtk-run       - Run the GTK application"
	@echo "  gtk-test      - Run GTK package tests"
	@echo ""
	@echo "Combined:"
	@echo "  all           - Build Java CLI and install GTK package"
	@echo "  dev           - Install Java dist and GTK package for development"

# Java targets
build:
	./gradlew build --no-daemon

install:
	./gradlew installDist --no-daemon

test:
	./gradlew test --no-daemon

integration:
	./gradlew integrationTest --no-daemon

check:
	./gradlew check --no-daemon

clean:
	./gradlew clean --no-daemon

VENV = brmgen-gtk/.venv
PYTHON = $(VENV)/bin/python
PIP = $(VENV)/bin/pip

# GTK targets
$(VENV):
	python3 -m venv $(VENV) --system-site-packages
	$(PIP) install setuptools

gtk-build: $(VENV)
	$(PIP) install -e brmgen-gtk --no-build-isolation

gtk-install: $(VENV)
	$(PIP) install -e "brmgen-gtk[dev]" --no-build-isolation

gtk-run: gtk-build
	$(VENV)/bin/brmgen-gtk

gtk-test: gtk-install
	$(PYTHON) -m pytest brmgen-gtk

# Combined targets
all: install gtk-install

dev: install gtk-install
	@echo ""
	@echo "Development environment ready!"
	@echo "Run 'make gtk-run' to start the GTK interface"
	@echo "Run './build/install/brmgen/bin/brmgen --help' for CLI help"