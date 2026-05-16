<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Codeowls Changelog

## [Unreleased]

## [1.0.0] - 2026-05-16

### Added

- CODEOWNERS language support for files literally named `CODEOWNERS`, anywhere in the project (`.github/`, `.gitlab/`,
  `docs/`, repository root).
- Syntax highlighting for:
  - Comments
  - Glob patterns, including GitLab negation patterns (`!/config/**/*.rb`)
  - User (`@alice`), team (`@org/team`), role (`@@maintainer`), and e-mail owners
  - GitLab section headers (`[Backend]`), optional sections (`^[Backend]`), approval counts (`[2]`), and same-line
    default owners
- Color settings page under *Settings → Editor → Color Scheme → CODEOWNERS*, with fallbacks to standard IDE colors so
  the plugin looks reasonable in any theme.
- Status bar widget that resolves the owners of the currently selected file and navigates to the matching rule on click.
- Declarative inlay hints showing the number of files each glob rule matches in the current project, plus a
  `Toggle CODEOWNERS File Count Hints` action reachable from Find Action.
- Path completion for glob patterns and owner completion drawn from owners already declared in the file.
- Code folding for GitLab section blocks.

[Unreleased]: https://github.com/wolfig/codeowls/compare/v1.0.0...HEAD

[1.0.0]: https://github.com/wolfig/codeowls/releases/tag/v1.0.0
