package net.wolfig.codeowls.matcher;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Compiles a CODEOWNERS glob pattern into an anchored {@link Pattern} that
 * matches forward-slash separated, project-relative file paths (no leading
 * {@code /}).
 *
 * <p>The implemented semantics follow GitHub's documented rules, which are a
 * subset of {@code .gitignore}:
 * <ul>
 *   <li>{@code *} — matches any run of characters within a single path
 *       segment (does not cross {@code /}).</li>
 *   <li>{@code **} — matches across path segments, including zero segments.
 *       {@code foo/**} matches any descendant of {@code foo}.</li>
 *   <li>{@code ?} — matches exactly one non-{@code /} character.</li>
 *   <li>A pattern starting with {@code /} is anchored to the repo root.</li>
 *   <li>A pattern containing {@code /} (but not at the start) is also anchored
 *       to the repo root — this matches GitHub's behavior and differs from
 *       gitignore.</li>
 *   <li>A pattern with no {@code /} (e.g. {@code *.java}) matches files with
 *       that name at any depth.</li>
 *   <li>A pattern ending with {@code /} matches that directory and everything
 *       under it.</li>
 *   <li>A pattern that matches a directory implicitly matches everything
 *       under it as well.</li>
 * </ul>
 *
 * <p>A leading {@code !} (GitLab negation) is stripped here; the caller decides
 * how to use negation when resolving ownership. Last-match-wins resolution
 * makes negation rarely needed in practice.
 */
public final class CodeownersGlob {

  private CodeownersGlob() {
  }

  /**
   * Compile {@code glob} into an anchored regex. Never returns {@code null}.
   */
  public static @NotNull Pattern compile(@NotNull String glob) {
    String g = glob;
    if (g.startsWith("!")) g = g.substring(1);

    boolean rooted = g.startsWith("/");
    if (rooted) g = g.substring(1);

    boolean dirOnly = g.endsWith("/");
    if (dirOnly) g = g.substring(0, g.length() - 1);

    boolean hasSlash = g.contains("/");

    StringBuilder re = new StringBuilder();
    re.append("^");
    // If the pattern has no slash and is not rooted, it can match a basename
    // at any depth. Otherwise, the pattern is anchored to the repo root.
    if (!rooted && !hasSlash) {
      re.append("(?:.*/)?");
    }
    appendGlob(re, g);
    if (dirOnly) {
      // Directory-only pattern: match anything beneath that directory.
      re.append("/.*");
    } else {
      // Any rule that matches a directory also matches its contents.
      re.append("(?:/.*)?");
    }
    re.append("$");
    return Pattern.compile(re.toString());
  }

  private static void appendGlob(StringBuilder re, @NotNull String g) {
    int i = 0;
    int n = g.length();
    while (i < n) {
      char c = g.charAt(i);
      if (c == '*') {
        boolean doubleStar = i + 1 < n && g.charAt(i + 1) == '*';
        if (doubleStar) {
          // Consume "**" and an optional trailing "/" so that "foo/**/bar" or
          // "foo/**" both behave as "any depth, including zero".
          re.append(".*");
          i += 2;
          if (i < n && g.charAt(i) == '/') i++;
        } else {
          re.append("[^/]*");
          i++;
        }
      } else if (c == '?') {
        re.append("[^/]");
        i++;
      } else if (c == '\\' && i + 1 < n) {
        // Escape sequence — emit the next char as a literal.
        re.append(Pattern.quote(String.valueOf(g.charAt(i + 1))));
        i += 2;
      } else if ("\\.^$+|(){}[]".indexOf(c) >= 0) {
        re.append('\\').append(c);
        i++;
      } else {
        re.append(c);
        i++;
      }
    }
  }
}
