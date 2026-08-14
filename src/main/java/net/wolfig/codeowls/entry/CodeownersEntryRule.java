package net.wolfig.codeowls.entry;

import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Generates and validates one rule for the "Add CODEOWNERS Entry" workflow.
 */
public final class CodeownersEntryRule {

  private CodeownersEntryRule() {
  }

  public static @NotNull String pattern(@NotNull String repositoryRelativePath,
                                        @NotNull PathMode mode) {
    String normalized = repositoryRelativePath.replace('\\', '/');
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    if (mode == PathMode.FILE_NAME) {
      int slash = normalized.lastIndexOf('/');
      return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
    return "/" + normalized;
  }

  public static @NotNull String normalizeOwners(@Nullable String owners) {
    if (owners == null) return "";
    String trimmed = owners.trim();
    return trimmed.isEmpty() ? "" : String.join(" ", trimmed.split("\\s+"));
  }

  public static boolean hasValidOwners(@Nullable String owners) {
    String normalized = normalizeOwners(owners);
    if (normalized.isEmpty()) return false;
    return Arrays.stream(normalized.split(" "))
            .allMatch(CodeownersOwnerRefactoring::isValidOwner);
  }

  public static @Nullable String build(@NotNull String pattern, @Nullable String owners) {
    String normalizedOwners = normalizeOwners(owners);
    if (!hasValidOwners(normalizedOwners)) return null;

    String candidate = pattern + " " + normalizedOwners;
    List<CodeownersRule> parsed = CodeownersRuleParser.parse(candidate, null);
    if (parsed.size() != 1) return null;
    CodeownersRule rule = parsed.getFirst();
    List<String> expectedOwners = List.of(normalizedOwners.split(" "));
    return rule.pattern().equals(pattern) && rule.owners().equals(expectedOwners)
            ? candidate
            : null;
  }

  public static @Nullable CodeownersRule existingExactRule(@NotNull List<CodeownersRule> rules,
                                                           @NotNull String exactPattern) {
    CodeownersRule found = null;
    for (CodeownersRule rule : rules) {
      if (rule.pattern().equals(exactPattern)) found = rule;
    }
    return found;
  }

  public enum PathMode {
    EXACT,
    FILE_NAME
  }
}
