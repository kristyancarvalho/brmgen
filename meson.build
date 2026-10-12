project('brmgen', meson_version: '>=1.0.0')

source_root = meson.project_source_root()
gradlew = find_program(join_paths(source_root, 'gradlew'))
python = find_program('python3')
shell = find_program('sh')
gtk_source = join_paths(source_root, 'brmgen-gtk')
gtk_venv = join_paths(gtk_source, '.venv')
gtk_python = join_paths(gtk_venv, 'bin', 'python')

run_target(
    'help',
    command: [
        python,
        '-c',
        'print("brmgen Meson tasks:"); print(); print("Java: java-build java-install java-test java-integration java-check java-clean"); print("GTK: gtk-build gtk-install gtk-run gtk-test"); print("Combined: setup-all dev"); print(); print("Run with: meson compile -C <builddir> <task>")',
    ],
)

run_target(
    'java-build',
    command: [gradlew, '--project-dir', source_root, 'build', '--no-daemon'],
)
run_target(
    'java-install',
    command: [
        gradlew,
        '--project-dir',
        source_root,
        'installDist',
        '--no-daemon',
    ],
)
run_target(
    'java-test',
    command: [gradlew, '--project-dir', source_root, 'test', '--no-daemon'],
)
run_target(
    'java-integration',
    command: [
        gradlew,
        '--project-dir',
        source_root,
        'integrationTest',
        '--no-daemon',
    ],
)
run_target(
    'java-check',
    command: [gradlew, '--project-dir', source_root, 'check', '--no-daemon'],
)
run_target(
    'java-clean',
    command: [gradlew, '--project-dir', source_root, 'clean', '--no-daemon'],
)

gtk_venv_ready = custom_target(
    'gtk-venv',
    output: 'gtk-venv.stamp',
    command: [
        shell,
        '-c',
        'if [ ! -x "$1/bin/python" ]; then "$3" -m venv --system-site-packages "$1"; "$1/bin/python" -m pip install setuptools; fi; touch "$2"',
        'gtk-venv',
        gtk_venv,
        '@OUTPUT@',
        python,
    ],
    build_by_default: false,
    build_always_stale: true,
    console: true,
)

run_target(
    'gtk-build',
    command: [
        gtk_python,
        '-m',
        'pip',
        'install',
        '-e',
        gtk_source,
        '--no-build-isolation',
    ],
    depends: [gtk_venv_ready],
)

run_target(
    'gtk-install',
    command: [
        gtk_python,
        '-m',
        'pip',
        'install',
        '-e',
        gtk_source + '[dev]',
        '--no-build-isolation',
    ],
    depends: [gtk_venv_ready],
)

run_target(
    'gtk-run',
    command: [
        shell,
        '-c',
        '"$1" -m pip install -e "$2" --no-build-isolation && "$3/bin/brmgen-gtk"',
        'gtk-run',
        gtk_python,
        gtk_source,
        gtk_venv,
    ],
    depends: [gtk_venv_ready],
)

run_target(
    'gtk-test',
    command: [
        shell,
        '-c',
        '"$1" -m pip install -e "$2[dev]" --no-build-isolation && "$1" -m pytest "$2"',
        'gtk-test',
        gtk_python,
        gtk_source,
    ],
    depends: [gtk_venv_ready],
)

run_target(
    'setup-all',
    command: [
        shell,
        '-c',
        '"$1" --project-dir "$2" installDist --no-daemon && "$3" -m pip install -e "$4[dev]" --no-build-isolation',
        'brmgen-all',
        gradlew,
        source_root,
        gtk_python,
        gtk_source,
    ],
    depends: [gtk_venv_ready],
)

run_target(
    'dev',
    command: [
        shell,
        '-c',
        '"$1" --project-dir "$2" installDist --no-daemon && "$3" -m pip install -e "$4[dev]" --no-build-isolation && echo && echo "Development environment ready!" && echo "Run meson compile -C <builddir> gtk-run to start the GTK interface." && echo "Run build/install/brmgen/bin/brmgen --help for CLI help."',
        'brmgen-dev',
        gradlew,
        source_root,
        gtk_python,
        gtk_source,
    ],
    depends: [gtk_venv_ready],
)
