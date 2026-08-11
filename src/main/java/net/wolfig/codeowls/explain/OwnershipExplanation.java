package net.wolfig.codeowls.explain;

import com.intellij.openapi.vfs.VirtualFile;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The full evaluation trace of CODEOWNERS ownership for one file: every rule
 * that matched it (in file / evaluation order) and which one ultimately wins.
 *
 * <p>Produced by {@code CodeownersService.explain(file)} using the exact same
 * matching pass as {@code resolveOwners(file)}, so the effective owner reported
 * here can never drift from the status-bar / owner-lookup result. Immutable, so
 * it is safe to compute under a background read action and consume on the EDT.
 *
 * @param targetFile     the file whose ownership is being explained
 * @param relativePath   {@code targetFile}'s path relative to the governing
 *                       CODEOWNERS root, or {@code null} when none governs it
 * @param codeownersFile the CODEOWNERS file that governs {@code targetFile}, or
 *                       {@code null} when no CODEOWNERS file is reachable
 * @param matchedRules   the matching rules in evaluation order; the last one is
 *                       flagged {@link MatchedRule#effective()}
 */
public record OwnershipExplanation(
        @NotNull VirtualFile targetFile,
        @Nullable String relativePath,
        @Nullable VirtualFile codeownersFile,
        @NotNull List<MatchedRule> matchedRules) {

  /**
   * Builds an explanation from the matching rules (in evaluation order),
   * marking the last — the last-match-wins winner — as effective.
   */
  public static @NotNull OwnershipExplanation of(@NotNull VirtualFile targetFile,
                                                 @NotNull String relativePath,
                                                 @NotNull VirtualFile codeownersFile,
                                                 @NotNull List<CodeownersRule> matching) {
    List<MatchedRule> matched = new ArrayList<>(matching.size());
    for (int i = 0; i < matching.size(); i++) {
      matched.add(new MatchedRule(matching.get(i), i == matching.size() - 1));
    }
    return new OwnershipExplanation(targetFile, relativePath, codeownersFile, List.copyOf(matched));
  }

  /**
   * No CODEOWNERS file governs {@code targetFile}.
   */
  public static @NotNull OwnershipExplanation noCodeowners(@NotNull VirtualFile targetFile) {
    return new OwnershipExplanation(targetFile, null, null, List.of());
  }

  /**
   * @return {@code true} when a governing CODEOWNERS file exists (even if no rule matches).
   */
  public boolean hasCodeownersFile() {
    return codeownersFile != null;
  }

  /**
   * @return {@code true} when at least one rule matches the file.
   */
  public boolean hasMatch() {
    return !matchedRules.isEmpty();
  }

  /**
   * @return the winning (effective) rule, or {@code null} when no rule matches.
   */
  public @Nullable MatchedRule effectiveRule() {
    return matchedRules.isEmpty() ? null : matchedRules.getLast();
  }

  /**
   * @return the effective owners for the file (last-match-wins), or empty when no rule matches.
   */
  public @NotNull List<String> effectiveOwners() {
    MatchedRule effective = effectiveRule();
    return effective == null ? List.of() : effective.resolvedOwners();
  }

  /**
   * @return the effective required approval count, or {@code null} when none applies.
   */
  public @Nullable Integer effectiveApprovalCount() {
    MatchedRule effective = effectiveRule();
    return effective == null ? null : effective.approvalCount();
  }
}
