# brmgen-gtk

GTK interface for brmgen.

## Requirements

- Python 3.11+
- GTK 4
- libadwaita 1
- brmgen CLI (built from the Java project)

## Installation

```bash
cd brmgen-gtk
pip install -e .
```

## Usage

```bash
brmgen-gtk
```

Or run directly:

```bash
python -m brmgen_gtk
```

## Building brmgen CLI

Before using the GTK interface, build the Java CLI:

```bash
cd ..
./gradlew installDist
```

This creates the CLI at `build/install/brmgen/bin/brmgen` which the GTK interface will auto-detect.