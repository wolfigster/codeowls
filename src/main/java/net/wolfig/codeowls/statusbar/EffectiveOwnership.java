package net.wolfig.codeowls.statusbar;

import com.intellij.openapi.vfs.VirtualFile;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Effective CODEOWNERS result for one project file.
 */
public record EffectiveOwnership(
        @Nullable VirtualFile codeownersFile,
        @Nullable VirtualFile repositoryRoot,
        @Nullable String relativePath,
        @Nullable CodeownersRule rule) {

  public static final EffectiveOwnership NONE = new EffectiveOwnership(null, null, null, null);

  public boolean isEmpty() {
    return rule == null;
  }

  public @NotNull List<String> owners() {
    return rule == null ? List.of() : rule.owners();
  }
}
