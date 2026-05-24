package net.wolfig.codeowls.suggestion;

import net.wolfig.codeowls.completion.CodeownersOwnerCollector;
import net.wolfig.codeowls.completion.CodeownersOwnerCollector.OwnerCandidate;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks owner candidates by how well they fit a given file, producing the
 * confidence-scored {@link OwnerSuggestion}s shown when a file has no CODEOWNERS
 * owner yet.
 *
 * <p>The candidate set is the same one the completion popup uses
 * ({@link CodeownersOwnerCollector}); this class only attaches a confidence
 * score and orders by it. Confidence is a weighted blend of three signals, each
 * in {@code [0, 1]}:
 *
 * <ul>
 *   <li><b>Path proximity</b> ({@value #W_PROXIMITY}): how close the owner's
 *       existing CODEOWNERS rules sit to the target file. An owner of the file's
 *       immediate directory scores 1.0; an owner of a distant ancestor scores
 *       lower; an owner with no related rule scores 0.</li>
 *   <li><b>Git author share</b> ({@value #W_GIT}): the owner's share of commits
 *       that touched the file (only applies to e-mail owners that match a Git
 *       author).</li>
 *   <li><b>Source prior</b> ({@value #W_SOURCE}): reliability of the source —
 *       owners already used in this CODEOWNERS file rank above Git contributors,
 *       which rank above built-in roles.</li>
 * </ul>
 *
 * <p>The math is pure and deterministic, so it is unit-tested without the
 * IntelliJ platform.
 */
public final class CodeownersOwnerSuggester {

  static final double W_PROXIMITY = 0.5;
  static final double W_GIT = 0.3;
  static final double W_SOURCE = 0.2;

  /**
   * Upper bound on suggestions returned, so the popup stays scannable even in
   * repositories with hundreds of Git contributors.
   */
  static final int MAX_SUGGESTIONS = 12;

  private CodeownersOwnerSuggester() {
  }

  /**
   * @param relativePath      target file path, repo-relative, forward slashes
   * @param rules             rules parsed from the active CODEOWNERS file
   * @param candidates        owner candidates (same set as completion)
   * @param fileAuthorCommits Git author e-mail → number of commits to the file
   * @return suggestions ordered by descending confidence, capped at
   * {@link #MAX_SUGGESTIONS}
   */
  public static @NotNull List<OwnerSuggestion> suggest(@NotNull String relativePath,
                                                       @NotNull List<CodeownersRule> rules,
                                                       @NotNull List<OwnerCandidate> candidates,
                                                       @NotNull Map<String, Integer> fileAuthorCommits) {
    String[] targetDir = directorySegments(relativePath);
    int totalCommits = fileAuthorCommits.values().stream().mapToInt(Integer::intValue).sum();

    List<OwnerSuggestion> out = new ArrayList<>(candidates.size());
    for (OwnerCandidate candidate : candidates) {
      double proximity = ownerProximity(candidate.owner(), rules, relativePath, targetDir);
      double gitShare = totalCommits == 0
              ? 0.0
              : (double) fileAuthorCommits.getOrDefault(candidate.owner(), 0) / totalCommits;
      double prior = sourcePrior(candidate.source());
      double confidence = clamp01(W_PROXIMITY * proximity + W_GIT * gitShare + W_SOURCE * prior);
      out.add(new OwnerSuggestion(candidate.owner(), candidate.source(), confidence));
    }

    out.sort(Comparator
            .comparingDouble(OwnerSuggestion::confidence).reversed()
            .thenComparing(Comparator.comparingDouble((OwnerSuggestion s) -> sourcePrior(s.source())).reversed())
            .thenComparing(OwnerSuggestion::owner));

    return out.size() > MAX_SUGGESTIONS ? new ArrayList<>(out.subList(0, MAX_SUGGESTIONS)) : out;
  }

  /**
   * Best proximity across all rules that list {@code owner}.
   */
  static double ownerProximity(@NotNull String owner, @NotNull List<CodeownersRule> rules,
                               @NotNull String relativePath, @NotNull String[] targetDir) {
    double best = 0.0;
    for (CodeownersRule rule : rules) {
      if (!rule.owners().contains(owner)) continue;
      best = Math.max(best, ruleProximity(rule, relativePath, targetDir));
    }
    return best;
  }

  /**
   * Proximity of one rule to the target: 1.0 if the rule already matches the
   * file, otherwise the fraction of the file's directory segments covered by the
   * rule's literal directory prefix.
   */
  static double ruleProximity(@NotNull CodeownersRule rule, @NotNull String relativePath,
                              @NotNull String[] targetDir) {
    if (rule.matches(relativePath)) return 1.0;
    String[] ruleDir = literalDirSegments(rule.pattern());
    int shared = commonPrefixLength(ruleDir, targetDir);
    int denom = Math.max(targetDir.length, 1);
    return Math.min(1.0, (double) shared / denom);
  }

  /**
   * Leading literal path segments of {@code pattern} — everything before the
   * first segment containing a glob metacharacter. Leading {@code !} / {@code /}
   * and a trailing {@code /} are stripped first.
   */
  static @NotNull String[] literalDirSegments(@NotNull String pattern) {
    String p = pattern;
    if (p.startsWith("!")) p = p.substring(1);
    if (p.startsWith("/")) p = p.substring(1);
    if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    if (p.isEmpty()) return new String[0];

    List<String> literal = new ArrayList<>();
    for (String segment : p.split("/")) {
      if (hasGlobMeta(segment)) break;
      literal.add(segment);
    }
    return literal.toArray(new String[0]);
  }

  /**
   * Directory segments of {@code relativePath} — the path without its last (file) segment.
   */
  static @NotNull String[] directorySegments(@NotNull String relativePath) {
    String p = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
    if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    if (p.isEmpty()) return new String[0];
    String[] segs = p.split("/");
    if (segs.length <= 1) return new String[0];
    String[] dir = new String[segs.length - 1];
    System.arraycopy(segs, 0, dir, 0, segs.length - 1);
    return dir;
  }

  private static int commonPrefixLength(@NotNull String[] a, @NotNull String[] b) {
    int n = Math.min(a.length, b.length);
    int i = 0;
    while (i < n && a[i].equals(b[i])) i++;
    return i;
  }

  private static boolean hasGlobMeta(@NotNull String segment) {
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      if (c == '*' || c == '?' || c == '[' || c == ']') return true;
    }
    return false;
  }

  private static double sourcePrior(@NotNull String source) {
    return switch (source) {
      case CodeownersOwnerCollector.SOURCE_CURRENT_FILE -> 1.0;
      case CodeownersOwnerCollector.SOURCE_GIT_HISTORY -> 0.6;
      case CodeownersOwnerCollector.SOURCE_BUILTIN_ROLE -> 0.2;
      default -> 0.4;
    };
  }

  private static double clamp01(double v) {
    return v < 0.0 ? 0.0 : Math.min(v, 1.0);
  }
}
