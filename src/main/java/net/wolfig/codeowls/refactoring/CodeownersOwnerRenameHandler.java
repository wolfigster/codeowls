package net.wolfig.codeowls.refactoring;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wires the platform's Rename refactoring (Shift+F6) to
 * {@link RefactorCodeownersOwnerAction} when the caret is on a CODEOWNERS owner.
 *
 * <p>CODEOWNERS has no PSI references to rename — owners are plain leaf tokens —
 * so the standard rename machinery has nothing to work with and would report
 * "cannot be renamed". Claiming the data context here replaces that dead end
 * with the owner-refactoring dialog, which is what Shift+F6 on an owner should
 * mean. The handler declines every other context, leaving rename in all other
 * files untouched.
 */
public final class CodeownersOwnerRenameHandler implements RenameHandler {

  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    return RefactorCodeownersOwnerAction.ownerElementAt(editor, file) != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @Nullable Editor editor,
                     @Nullable PsiFile file,
                     @Nullable DataContext dataContext) {
    RefactorCodeownersOwnerAction.refactorOwnerAtCaret(project, editor, file);
  }

  /**
   * Owners only exist at a caret position, so the element-based entry point
   * (Rename invoked from a tree view, for instance) has nothing to do.
   */
  @Override
  public void invoke(@NotNull Project project,
                     PsiElement @NotNull [] elements,
                     @Nullable DataContext dataContext) {
  }
}
