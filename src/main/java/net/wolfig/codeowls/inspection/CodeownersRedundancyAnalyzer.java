package net.wolfig.codeowls.inspection;

import net.wolfig.codeowls.matcher.CodeownersRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds CODEOWNERS rules that carry no weight, given the project's files.
 *
 * <p>Resolution is last-match-wins, so a single pass over the project files is
 * enough to classify every rule:
 * <ul>
 *   <li>{@link Kind#NO_FILES_MATCH} — the rule matches no project file at all
 *       (the path/glob points at something that does not exist).</li>
 *   <li>{@link Kind#SHADOWED} — the rule matches files, but a <em>later</em>
 *       rule wins for every one of them, so it never decides ownership. The
 *       index of an overriding rule is reported for the diagnostic message.</li>
 * </ul>
 *
 * <p>A rule that wins for at least one file is never flagged, even if other
 * rules also cover some of its files. Negation patterns ({@code !…}) are not
 * analysed: {@link net.wolfig.codeowls.matcher.CodeownersGlob} strips the
 * {@code !}, so treating them as positive matches here would mislead.
 *
 * <p>Pure and deterministic — unit-tested without the IntelliJ platform.
 */
public final class CodeownersRedundancyAnalyzer {

  private CodeownersRedundancyAnalyzer() {
  }

  public static @NotNull List<Finding> analyze(@NotNull List<CodeownersRule> rules,
                                               @NotNull List<String> projectFilePaths) {
    int n = rules.size();
    if (n == 0) return List.of();

    boolean[] analyzable = new boolean[n];
    for (int i = 0; i < n; i++) analyzable[i] = isAnalyzable(rules.get(i).pattern());

    boolean[] matchedAny = new boolean[n];
    boolean[] everWinner = new boolean[n];
    int[] shadowedBy = new int[n];
    Arrays.fill(shadowedBy, -1);

    List<Integer> matched = new ArrayList<>();
    for (String path : projectFilePaths) {
      matched.clear();
      for (int i = 0; i < n; i++) {
        if (analyzable[i] && rules.get(i).matches(path)) {
          matched.add(i);
          matchedAny[i] = true;
        }
      }
      if (matched.isEmpty()) continue;
      int winner = matched.get(matched.size() - 1);
      everWinner[winner] = true;
      // Every earlier matching rule is overridden by `winner` for this file.
      for (int k = 0; k < matched.size() - 1; k++) {
        int shadowed = matched.get(k);
        if (shadowedBy[shadowed] < 0) shadowedBy[shadowed] = winner;
      }
    }

    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      if (!analyzable[i]) continue;
      if (!matchedAny[i]) {
        findings.add(new Finding(i, Kind.NO_FILES_MATCH, -1));
      } else if (!everWinner[i]) {
        findings.add(new Finding(i, Kind.SHADOWED, shadowedBy[i]));
      }
    }
    return findings;
  }

  static boolean isAnalyzable(@NotNull String pattern) {
    return !pattern.isBlank() && !pattern.startsWith("!");
  }

  public enum Kind {NO_FILES_MATCH, SHADOWED}

  /**
   * @param ruleIndex          index of the unnecessary rule
   * @param kind               why it is unnecessary
   * @param shadowingRuleIndex for {@link Kind#SHADOWED}, the index of a rule that
   *                           overrides it; {@code -1} otherwise
   */
  public record Finding(int ruleIndex, @NotNull Kind kind, int shadowingRuleIndex) {
  }
}
