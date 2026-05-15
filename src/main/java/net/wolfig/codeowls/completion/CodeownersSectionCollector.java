package net.wolfig.codeowls.completion;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans a CODEOWNERS file for GitLab-style section header names — the bit
 * between the first {@code [...]} on a header line. Used by
 * {@link CodeownersSectionCompletionProvider} to suggest names that are
 * already in use elsewhere in the file.
 *
 * <p>Section detection mirrors {@link net.wolfig.codeowls.folding.CodeownersFoldingBuilder}'s
 * recognizer: any non-comment line whose first non-whitespace content starts
 * with {@code [} (optionally preceded by {@code ^} for an optional section)
 * and contains a matching {@code ]}.
 */
public final class CodeownersSectionCollector {

  private CodeownersSectionCollector() {
  }

  public static @NotNull List<String> collect(@NotNull PsiFile codeownersFile) {
    CharSequence text = codeownersFile.getViewProvider().getContents();
    if (text.isEmpty()) return List.of();
    Set<String> seen = new LinkedHashSet<>();
    int lineStart = 0;
    int n = text.length();
    for (int i = 0; i <= n; i++) {
      if (i == n || text.charAt(i) == '\n') {
        String name = parseSectionName(text, lineStart, i);
        if (name != null) seen.add(name);
        lineStart = i + 1;
      }
    }
    return new ArrayList<>(seen);
  }

  /**
   * @return the section name on {@code [start, end)} of {@code text}, or {@code null}.
   */
  static String parseSectionName(@NotNull CharSequence text, int start, int end) {
    int i = start;
    while (i < end && isHSpace(text.charAt(i))) i++;
    if (i >= end) return null;
    char c = text.charAt(i);
    if (c == '^') {
      i++;
      if (i >= end || text.charAt(i) != '[') return null;
    } else if (c != '[') {
      return null;
    }
    int nameStart = i + 1;
    int close = -1;
    for (int j = nameStart; j < end; j++) {
      if (text.charAt(j) == ']') {
        close = j;
        break;
      }
    }
    if (close < 0 || close == nameStart) return null;
    return text.subSequence(nameStart, close).toString();
  }

  private static boolean isHSpace(char c) {
    return c == ' ' || c == '\t';
  }
}
