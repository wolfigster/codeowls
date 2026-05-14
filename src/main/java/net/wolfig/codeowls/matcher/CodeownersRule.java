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
 */
public record CodeownersRule(
        @NotNull String pattern,
        @NotNull List<String> owners,
        @NotNull Pattern compiledPattern,
        @Nullable VirtualFile sourceFile,
        int lineNumber) {

  /**
   * @return {@code true} if {@code relativePath} (forward-slash, no leading /) is matched by this rule.
   */
  public boolean matches(@NotNull String relativePath) {
    return compiledPattern.matcher(relativePath).matches();
  }
}
