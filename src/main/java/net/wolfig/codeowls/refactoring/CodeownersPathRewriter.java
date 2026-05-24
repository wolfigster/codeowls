package net.wolfig.codeowls.refactoring;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rewrites a single CODEOWNERS pattern when the path it targets is moved or
 * renamed. Pure string logic with no IntelliJ dependencies, so it is unit-tested
 * directly.
 *
 * <p>Only patterns that <em>literally reference</em> the moved path are rewritten:
 * the exact path, the directory forms ({@code path/}, {@code path/**}), and
 * anything nested under a moved directory ({@code path/sub/…}, {@code path/*.ext}).
 * A leading {@code !} (negation) and {@code /} (anchor) are preserved. Basename
 * rules and unrelated globs (e.g. {@code *.java}) are left untouched — moving a
 * file does not change which basenames a glob would match.
 *
 * <p>Paths are repo-relative, forward-slash, with no leading or trailing slash.
 */
public final class CodeownersPathRewriter {

  private CodeownersPathRewriter() {
  }

  /**
   * @return the rewritten pattern, or {@code null} if {@code pattern} does not
   * reference {@code oldPath} and so should be left unchanged.
   */
  public static @Nullable String rewritePattern(@NotNull String pattern,
                                                @NotNull String oldPath,
                                                @NotNull String newPath) {
    String core = pattern;
    String prefix = "";
    if (core.startsWith("!")) {
      prefix = "!";
      core = core.substring(1);
    }
    if (core.startsWith("/")) {
      prefix += "/";
      core = core.substring(1);
    }

    String rewritten;
    if (core.equals(oldPath)) {
      rewritten = newPath;
    } else if (core.startsWith(oldPath + "/")) {
      // Nested under a moved directory: keep the suffix, swap the prefix.
      rewritten = newPath + core.substring(oldPath.length());
    } else {
      return null;
    }
    return prefix + rewritten;
  }
}
