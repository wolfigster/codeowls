package net.wolfig.codeowls.refactoring;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.OwnerToken;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.Plan;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.SectionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * "Refactor Owner…" — replaces the owner under the caret with another one,
 * either throughout the enclosing GitLab section or throughout the whole
 * CODEOWNERS file.
 *
 * <p>Enablement is decided on the PSI: the leaf at the caret must carry one of
 * the lexer's owner element types. Everything else in a CODEOWNERS file —
 * patterns, comments, section names, approval counts, whitespace — has a
 * different element type, so the action simply does not appear there, and it is
 * inert in every other language.
 *
 * <p>The replacement itself is planned by {@link CodeownersOwnerRefactoring} and
 * executed by {@link CodeownersOwnerRefactoringCommand} as a single undoable
 * command. The same entry point backs {@link CodeownersOwnerRenameHandler}, so
 * Shift+F6 on an owner opens the same dialog.
 */
public final class RefactorCodeownersOwnerAction extends DumbAwareAction {

  /**
   * The owner element at the caret, or {@code null} when the caret is not on an
   * owner (or the file is not a CODEOWNERS file).
   *
   * <p>A caret sitting immediately behind an owner still counts, so
   * {@code @alice<caret>} resolves like {@code @al<caret>ice}. Must be called
   * under a read action.
   */
  public static @Nullable PsiElement ownerElementAt(@Nullable Editor editor, @Nullable PsiFile file) {
    if (editor == null || file == null || !file.getLanguage().is(CodeownersLanguage.INSTANCE)) return null;

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = ownerLeafAt(file, offset);
    return element != null ? element : (offset > 0 ? ownerLeafAt(file, offset - 1) : null);
  }

  private static @Nullable PsiElement ownerLeafAt(@NotNull PsiFile file, int offset) {
    PsiElement leaf = file.findElementAt(offset);
    if (leaf == null) return null;
    ASTNode node = leaf.getNode();
    return node != null && CodeownersRuleParser.isOwnerToken(node.getElementType()) ? leaf : null;
  }

  /**
   * Asks for a replacement owner and a scope, then performs the refactoring.
   * Does nothing when the caret is not on an owner, or when the user cancels.
   */
  public static void refactorOwnerAtCaret(@NotNull Project project,
                                          @Nullable Editor editor,
                                          @Nullable PsiFile file) {
    if (editor == null || file == null || ownerElementAt(editor, file) == null) return;

    // The PSI decided that this is an owner; the token itself is then read back
    // from the document the edit will target, so plan offsets and document
    // offsets can never disagree (e.g. over uncommitted PSI).
    CharSequence content = editor.getDocument().getImmutableCharSequence();
    OwnerToken owner = CodeownersOwnerRefactoring.ownerTokenAt(content, editor.getCaretModel().getOffset());
    if (owner == null) return;

    SectionRange section = CodeownersOwnerRefactoring.sectionAt(content, owner.startOffset());
    RefactorCodeownersOwnerDialog dialog =
            new RefactorCodeownersOwnerDialog(project, content, owner, section, file.getName());
    if (!dialog.showAndGet()) return;

    Plan plan = dialog.plan();
    CodeownersOwnerRefactoringCommand.execute(project, file, editor.getDocument(), plan);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean available = e.getProject() != null
            && ownerElementAt(e.getData(CommonDataKeys.EDITOR), e.getData(CommonDataKeys.PSI_FILE)) != null;
    e.getPresentation().setEnabledAndVisible(available);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    refactorOwnerAtCaret(project, e.getData(CommonDataKeys.EDITOR), e.getData(CommonDataKeys.PSI_FILE));
  }
}
