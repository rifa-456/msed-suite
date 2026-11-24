# AGENTS.md

Read the Conventions section of [CONTRIBUTING.md](CONTRIBUTING.md) before writing code, and
[ARCHITECTURE.md](ARCHITECTURE.md) before moving code between packages. The compilers, linters,
the layer test and the tests enforce the other rules: fix what they report instead of silencing
it.

## Commands

- Mod: `./gradlew build` in `mod/` (format check, strict compile, tests, jar);
  `./gradlew spotlessApply` formats; `./gradlew runClient` starts the game with the mod.
- Toolkit, in `toolkit/`: `uv sync` once, then `uv run ruff check .`,
  `uv run ruff format .`, `uv run mypy`, `uv run pydoclint --config=pyproject.toml src`,
  `uv run lint-imports` and `uv run pytest`.
- A `.proto` file changed: rebuild the mod and run `uv sync --reinstall-package msed` in `toolkit/`.

## Do not edit

- `toolkit/src/msed/v1/`: generated from `proto/` by the toolkit's build.
- `mod/build/` and `mod/run/`: build output and the development game directory.

## Gotchas

- A removed schema field keeps its number and name as `reserved`.
- Only one development client may run at a time; a second one cannot open the same world.
- The Java build treats every warning as an error, Error Prone and NullAway included.
- Mixins target Minecraft's Mojang-named classes for 1.21.11; check the target method's signature
  in the decompiled game before changing an injection point.
