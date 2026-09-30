import subprocess
import sys
from pathlib import Path


def find_brmgen_cli() -> Path:
    candidates = [
        Path("build/install/brmgen/bin/brmgen"),
        Path("../build/install/brmgen/bin/brmgen"),
        Path("brmgen"),
    ]
    for candidate in candidates:
        resolved = candidate.resolve()
        if resolved.exists() and resolved.is_file():
            return resolved
    return Path("brmgen")


BRMGEN_CLI = find_brmgen_cli()


def run_command(args: list[str], cwd: Path | None = None) -> subprocess.CompletedProcess:
    cmd = [str(BRMGEN_CLI)] + args
    return subprocess.run(cmd, capture_output=True, text=True, cwd=cwd)


def validate_model(input_path: Path) -> tuple[bool, str]:
    result = run_command(["validate", str(input_path)])
    return result.returncode == 0, result.stdout + result.stderr


def build_model(
    input_path: Path,
    output_path: Path | None = None,
    logical: bool = False,
    brmodelo_jar: Path | None = None,
    force: bool = False,
) -> tuple[bool, str]:
    args = ["build", str(input_path)]
    if output_path:
        args.extend(["-o", str(output_path)])
    if logical:
        args.append("--logical")
    if brmodelo_jar:
        args.extend(["--brmodelo-jar", str(brmodelo_jar)])
    if force:
        args.append("--force")
    result = run_command(args)
    return result.returncode == 0, result.stdout + result.stderr


def run_doctor(brmodelo_jar: Path | None = None) -> tuple[bool, str]:
    args = ["doctor"]
    if brmodelo_jar:
        args.extend(["--brmodelo-jar", str(brmodelo_jar)])
    result = run_command(args)
    return result.returncode == 0, result.stdout + result.stderr


def get_version() -> str:
    result = run_command(["version"])
    return result.stdout.strip()