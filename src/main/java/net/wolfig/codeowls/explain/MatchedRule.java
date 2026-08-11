package net.wolfig.codeowls.explain;

import com.intellij.openapi.vfs.VirtualFile;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One CODEOWNERS rule that matches the file being explained, together with the
 * verdict on whether it is the <em>effective</em> (winning) rule.
 *
 * <p>Thin wrapper over {@link CodeownersRule}: the source file, line, resolved
 * owners, approval count, and section provenance all already live on the rule,
 * so this record adds only the {@code effective} flag and convenience accessors
 * to avoid a second, drift-prone copy of that data.
 */
public record MatchedRule(@NotNull CodeownersRule rule, boolean effective) {

  public @NotNull String pattern() {
    return rule.pattern();
  }

  public @NotNull List<String> resolvedOwners() {
    return rule.owners();
  }

  public @Nullable Integer approvalCount() {
    return rule.approvalCount();
  }

  public @Nullable VirtualFile sourceFile() {
    return rule.sourceFile();
  }

  /**
   * @return 0-based line of the rule in its {@link #sourceFile()}.
   */
  public int line() {
    return rule.lineNumber();
  }

  /**
   * @return the enclosing GitLab section, or {@code null} when the rule is in none.
   */
  public @Nullable CodeownersSection section() {
    return rule.section();
  }

  /**
   * @return {@code true} when this rule's owners were inherited from its
   * section's default owners rather than declared on the rule itself.
   */
  public boolean inheritedFromSection() {
    return rule.ownersInherited();
  }
}
