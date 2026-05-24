package net.wolfig.codeowls.inlay;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Counts how many project files each CODEOWNERS rule matches.
 *
 * <p>The result is cached per CODEOWNERS {@link PsiFile} via
 * {@link CachedValuesManager} and invalidated on PSI changes. The inlay-hint
 * collector calls into this class on every pass; without the cache it would
 * re-walk the project tree and re-run every rule on every repaint.
 *
 * <p>Single-file (exact-path) rules and obviously irrelevant lines (comments,
 * empty lines, malformed lines, section headers — anything {@link
 * CodeownersRuleParser} doesn't surface as a rule) are skipped: a "1 file"
 * hint on a rule that already names a specific path adds nothing.
 *
 * <p>For tests and reuse the heavy lifting lives in
 * {@link #computeCountsByLine(CharSequence, VirtualFile, List)} — a pure
 * function that takes the CODEOWNERS text and a precomputed list of project
 * file paths.
 */
public final class CodeownersMatchCounter {

  /**
   * Directory names skipped when walking project files. Matches the path completion list.
   */
  static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
          ".git", ".idea", ".gradle", "node_modules", "build", "dist", "target", "out");

  private CodeownersMatchCounter() {
  }

  /**
   * @return a (line → match-count) map for {@code codeownersFile}, cached on
   * the PSI file and invalidated on any PSI modification. Lines that aren't
   * glob-style rules (comments, headers, malformed lines, exact-path rules)
   * are absent from the map rather than reported as zero.
   */
  public static @NotNull Map<Integer, Integer> countsByLine(@NotNull PsiFile codeownersFile) {
    return CachedValuesManager.getCachedValue(codeownersFile, () -> {
      Map<Integer, Integer> counts = compute(codeownersFile);
      return CachedValueProvider.Result.create(counts,
              codeownersFile, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static @NotNull Map<Integer, Integer> compute(@NotNull PsiFile codeownersFile) {
    VirtualFile vf = codeownersFile.getVirtualFile();
    VirtualFile root = projectRoot(vf);
    if (root == null) return Map.of();
    List<String> paths = collectProjectFilePaths(root);
    return computeCountsByLine(codeownersFile.getViewProvider().getContents(), vf, paths);
  }

  /**
   * Pure-logic core, exposed for tests. Parses {@code codeownersText} and,
   * for each glob-style rule, counts how many of {@code projectFilePaths}
   * (forward-slash, no leading {@code /}) the rule matches.
   */
  static @NotNull Map<Integer, Integer> computeCountsByLine(@NotNull CharSequence codeownersText,
                                                            @Nullable VirtualFile sourceForRules,
                                                            @NotNull List<String> projectFilePaths) {
    if (codeownersText.isEmpty()) return Map.of();
    List<CodeownersRule> rules = CodeownersRuleParser.parse(codeownersText, sourceForRules);
    Map<Integer, Integer> result = new HashMap<>();
    for (CodeownersRule rule : rules) {
      // Only emit hints for patterns whose match count is non-trivial: a glob
      // (contains *, ?, [, ]) or a directory pattern (ends with /). Anchored
      // file paths and bare basenames almost always resolve to a single file,
      // so the "// 1 file" hint just adds visual noise.
      if (!isGlobOrDirectoryPattern(rule.pattern())) continue;
      int matches = 0;
      for (String path : projectFilePaths) {
        if (rule.matches(path)) matches++;
      }
      result.put(rule.lineNumber(), matches);
    }
    return result;
  }

  /**
   * The directory CODEOWNERS patterns are written against. Mirrors the
   * heuristic used by {@code CodeownersPathCompletionProvider}: a CODEOWNERS
   * file under {@code .github/}, {@code .gitlab/}, or {@code docs/} resolves
   * to its grandparent; otherwise its parent.
   */
  public static @Nullable VirtualFile projectRoot(@Nullable VirtualFile codeownersFile) {
    if (codeownersFile == null) return null;
    VirtualFile parent = codeownersFile.getParent();
    if (parent == null) return null;
    String name = parent.getName();
    if (name.equals(".github") || name.equals(".gitlab") || name.equals("docs")) {
      VirtualFile gp = parent.getParent();
      return gp != null ? gp : parent;
    }
    return parent;
  }

  /**
   * Collect every regular file under {@code root}, returned as project-relative
   * forward-slash paths (no leading {@code /}). Ignored build/VCS folders are
   * skipped before recursion. Package-private for tests.
   */
  public static @NotNull List<String> collectProjectFilePaths(@NotNull VirtualFile root) {
    List<String> result = new ArrayList<>();
    walk(root, "", result);
    return result;
  }

  /**
   * @return {@code true} if {@code pattern} is either a directory pattern
   * (ends with {@code /}) or contains any glob metacharacter
   * ({@code *}, {@code ?}, {@code [}, {@code ]}). Patterns that satisfy
   * neither condition (anchored file paths and bare basenames) are excluded
   * from the inlay hint — they're either guaranteed to match a single file
   * or, in practice, almost always do.
   */
  static boolean isGlobOrDirectoryPattern(@NotNull String pattern) {
    if (pattern.isEmpty()) return false;
    if (pattern.endsWith("/")) return true;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '*' || c == '?' || c == '[' || c == ']') return true;
    }
    return false;
  }

  private static void walk(@NotNull VirtualFile dir, @NotNull String prefix, @NotNull List<String> out) {
    for (VirtualFile child : dir.getChildren()) {
      if (!child.isValid()) continue;
      String name = child.getName();
      String next = prefix.isEmpty() ? name : prefix + "/" + name;
      if (child.isDirectory()) {
        if (IGNORED_DIRECTORY_NAMES.contains(name)) continue;
        walk(child, next, out);
      } else {
        out.add(next);
      }
    }
  }
}
