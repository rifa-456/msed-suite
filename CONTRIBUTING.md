# Contributing to MSED

Fixes and documentation changes can go straight to a pull request. For a change to the schema
in `proto/`, open an issue first, since every recorded dataset depends on it.

A bug report needs your OS, the Minecraft, Fabric Loader and MSED versions, and what happened.
For a recording problem, attach the episode's `metadata.json` and the output of
`msed validate` on it; for a crash, the game's `latest.log`.

## Setup

The mod needs JDK 21. The toolkit needs Python 3.10 or newer and [uv](https://docs.astral.sh/uv/).
Video needs FFmpeg on `PATH`.

```sh
git clone https://github.com/rifa-456/msed-suite
cd msed-suite/mod
./gradlew build          # format check, strict compile, tests, jar
./gradlew runClient      # starts Minecraft with the mod

cd ../toolkit
uv sync                  # the toolkit and its dev tools, at the versions in uv.lock
```

The toolkit's build compiles `proto/` into `src/msed/v1`; those files are generated and never
committed.

## Checks

Before a pull request, run both sets:

```sh
cd mod && ./gradlew build
cd toolkit && uv run ruff check . && uv run ruff format --check . && uv run mypy \
    && uv run pydoclint --config=pyproject.toml src && uv run lint-imports && uv run pytest
```

`./gradlew spotlessApply` formats the Java code; `uv run ruff format .` formats the Python code.
The Java build fails on any compiler, Error Prone or NullAway warning, and `ArchitectureTest`
fails when a package uses a layer above it; `lint-imports` does the same for the toolkit. CI runs
the same checks.

## Documentation site

```sh
uv venv --python 3.13
uv pip install -r docs/requirements.txt ./toolkit
uv run --no-project mkdocs serve
```

The reference pages are generated on every build: the columns, schema and settings from the
`.proto` files and their comments, the command line from the click commands, the Python API from
the docstrings, and the versions in the guides from `mod/gradle.properties` and
`toolkit/pyproject.toml`. To change them, change the source; `docs/hooks/` holds the generators.
`mkdocs build --strict` fails on a broken link or an unknown version placeholder.

## Changing the schema

After editing a `.proto` file, rebuild the mod and reinstall the toolkit with
`uv sync --reinstall-package msed` in `toolkit/`, which regenerates `msed.v1`.

Versions follow SemVer, and the toolkit reads episodes of its own major version. Adding a field
or a message is a minor version. Removing or renumbering a field, or changing what a field means,
is a major version. A removed field's number and name are reserved so they are never reused.
Every field gets a comment with its unit, range and empty value. A setting in `config.proto`
takes its default from a `default_*` option; the mod and the docs both read it from there.

## Releasing

The mod and the toolkit share one version. To release it:

1. Set the version in `mod/gradle.properties` (`mod_version`) and `toolkit/pyproject.toml`.
2. Add its section to `CHANGELOG.md`, dated, with a link at the bottom.
3. Commit, then tag the commit `vX.Y.Z` and push the tag.

The tag starts `release.yml`. It checks that the tag, both versions and the changelog agree,
runs every CI check, publishes the toolkit to PyPI through trusted publishing, and creates the
GitHub release with the mod jar, the toolkit's wheel and sdist, build provenance attestations,
and the changelog section as its notes.

## Conventions

Ruff, mypy, pydoclint, Error Prone, NullAway, Spotless and the two layer checks enforce most
rules.
These are the ones no check catches.

### Code

- Files stay around 150 lines of code, and folders are named after what they hold in a
  recording: `observation`, `episode`, `vocabulary`, never `util` or `common`.
- A hook in `mixin/` calls MSED code and holds no logic of its own.
- Values are records in Java and frozen dataclasses in Python. An error the toolkit raises on
  purpose is defined in `msed/errors.py`.
- Comments say why, on their own line above the code.

### Documentation

- Javadoc starts with a third-person summary ("Returns…", "Writes…"); Python docstrings start
  with an imperative one ("Return…", "Write…"), in the Google style.
- Every public parameter, return value and exception is documented, with its unit, its range or
  what an empty value means.
- A class is described by what it is in a recording, in one plain sentence.
- Documentation says what the code does and how. It names rules directly rather than pointing to
  sections of the paper.

### Commits

Messages follow [Conventional Commits](https://www.conventionalcommits.org/), one change per
commit, with no AI co-author or session trailers. You may use AI tools, but you answer for every
line you submit.

Report security problems as [SECURITY.md](SECURITY.md) describes, not in public issues.
