<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Codeowls Changelog

## [Unreleased]

## [1.1.0] - 2026-05-24

### Added

- **GitLab section default owners**: rules inside a section that list no owners of their own now inherit the section's
  default owners (`[Documentation] @docs-team`); a rule's own owners override the default. The section's optional
  approval count (`[Backend][2]`) is likewise carried onto its rules.
- **Owner navigation**: Ctrl/Cmd+Click a user or team owner to open their GitHub / GitLab page in the browser, resolved
  from the repository's Git remote (`.git/config`).
- **Owner suggestions**: clicking the status bar widget for a file with no specific owner — no matching rule, or only the
  global wildcard rule (`*` / `**`) — opens a popup of confidence-ranked owner suggestions blending path proximity to
  existing rules, Git authorship of the file, and source reliability. Choosing one appends a rule to the CODEOWNERS file.
- **Matched-file navigation**: a gutter icon on each wildcard / directory rule lists the project files it matches;
  selecting one navigates to it.
- **Unnecessary-rule inspection**: flags patterns that match no project file (warning) and rules fully shadowed by a
  later rule (greyed-out, unused style), each with a quick fix to remove the rule line.
- **Move / rename sync**: when a file or directory is moved or renamed, CODEOWNERS rules that target it are rewritten to
  the new path automatically.
- Status bar widget now shows the section's required approval count in its tooltip when the matching rule's section
  declares one.

### Changed

- The status bar widget icon now shows the "no specific owner" glyph for files covered only by the global wildcard rule
  (`*` / `**`), matching the click-to-suggest behavior.

## [1.0.1] - 2026-05-16

### Changed

- Auto-popup of owner completion on `@` moved from the completion contributor into a dedicated `TypedHandlerDelegate`,
  aligning with the platform's recommended trigger path. No user-visible behavior change.

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

[Unreleased]: https://github.com/wolfigster/codeowls/compare/1.1.0...HEAD
[1.1.0]: https://github.com/wolfigster/codeowls/compare/1.0.1...1.1.0
[1.0.1]: https://github.com/wolfigster/codeowls/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/wolfigster/codeowls/releases/tag/1.0.0
