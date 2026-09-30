import shutil
import subprocess
import sys
from pathlib import Path


def find_brmgen_cli() -> Path | None:
    candidates = [
        Path("/usr/bin/brmgen"),
        Path("/usr/local/bin/brmgen"),
        Path("build/install/brmgen/bin/brmgen"),
        Path("../build/install/brmgen/bin/brmgen"),
    ]
    for candidate in candidates:
        resolved = candidate.resolve()
        if resolved.exists() and resolved.is_file():
            return resolved
    path_cli = shutil.which("brmgen")
    if path_cli:
        return Path(path_cli)
    return None


def check_cli_available() -> tuple[bool, str]:
    cli = find_brmgen_cli()
    if cli is None:
        return False, "brmgen CLI não encontrado no PATH nem em locais padrão"
    try:
        result = subprocess.run(
            [str(cli), "version"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        if result.returncode == 0:
            return True, result.stdout.strip()
        return False, f"brmgen CLI falhou: {result.stderr}"
    except subprocess.TimeoutExpired:
        return False, "brmgen CLI timeout"
    except Exception as e:
        return False, f"Erro ao executar brmgen CLI: {e}"


BRMGEN_CLI = find_brmgen_cli()


def run_command(args: list[str], cwd: Path | None = None) -> subprocess.CompletedProcess:
    cli = find_brmgen_cli()
    if cli is None:
        return subprocess.CompletedProcess(
            args=["brmgen"] + args,
            returncode=127,
            stdout="",
            stderr="brmgen CLI não encontrado",
        )
    cmd = [str(cli)] + args
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