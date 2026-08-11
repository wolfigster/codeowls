package net.wolfig.codeowls.matcher;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A single parsed CODEOWNERS rule: {@code pattern owner1 owner2 …}.
 *
 * <p>The {@link #compiledPattern} is a {@link Pattern} built by
 * {@link CodeownersGlob#compile(String)} that decides whether a given
 * project-relative file path is matched by this rule's glob.
 *
 * @param pattern         the raw glob exactly as it appears in the file
 * @param owners          owners listed after the pattern, in source order
 * @param compiledPattern compiled glob → regex (anchored, gitignore-style)
 * @param sourceFile      the CODEOWNERS file the rule was read from, or
 *                        {@code null} if it has been deleted since parsing
 * @param lineNumber      0-based line number of the rule in the source file
 * @param approvalCount   number of approvals required by the rule's enclosing
 *                        GitLab section (e.g. {@code [Backend][2]}), or
 *                        {@code null} when the rule is in no section or the
 *                        section declares no approval count
 * @param section         the enclosing GitLab section header, or {@code null}
 *                        when the rule is in no section. Retained purely so an
 *                        ownership explanation can describe inheritance; the
 *                        effective {@link #owners()} / {@link #approvalCount()}
 *                        are already resolved
 * @param ownersInherited {@code true} when {@link #owners()} were inherited from
 *                        {@link #section}'s default owners because the rule
 *                        declared none of its own
 */
public record CodeownersRule(
        @NotNull String pattern,
        @NotNull List<String> owners,
        @NotNull Pattern compiledPattern,
        @Nullable VirtualFile sourceFile,
        int lineNumber,
        @Nullable Integer approvalCount,
        @Nullable CodeownersSection section,
        boolean ownersInherited) {

  /**
   * Convenience constructor for a rule outside any GitLab section.
   */
  public CodeownersRule(@NotNull String pattern, @NotNull List<String> owners,
                        @NotNull Pattern compiledPattern, @Nullable VirtualFile sourceFile,
                        int lineNumber, @Nullable Integer approvalCount) {
    this(pattern, owners, compiledPattern, sourceFile, lineNumber, approvalCount, null, false);
  }

  /**
   * Convenience constructor for a rule with no section approval count.
   */
  public CodeownersRule(@NotNull String pattern, @NotNull List<String> owners,
                        @NotNull Pattern compiledPattern, @Nullable VirtualFile sourceFile,
                        int lineNumber) {
    this(pattern, owners, compiledPattern, sourceFile, lineNumber, null);
  }

  /**
   * @return {@code true} if {@code relativePath} (forward-slash, no leading /) is matched by this rule.
   */
  public boolean matches(@NotNull String relativePath) {
    return compiledPattern.matcher(relativePath).matches();
  }
}
