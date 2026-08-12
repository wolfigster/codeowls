package net.wolfig.codeowls.refactoring;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.Edit;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.Plan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Applies a {@link Plan} to a CODEOWNERS document.
 *
 * <p>Every edit of one refactoring runs inside a single
 * {@link WriteCommandAction} named "Refactor CODEOWNERS Owner", so the platform
 * treats it as one command: one entry in the Undo stack, one write action, and a
 * PSI/document commit at the end.
 *
 * <p>Edits are applied from the end of the file backwards so that the offsets of
 * the not-yet-applied edits stay valid.
 */
public final class CodeownersOwnerRefactoringCommand {

  /**
   * The Undo-stack label of the refactoring.
   */
  public static final String COMMAND_NAME = "Refactor CODEOWNERS Owner";

  private CodeownersOwnerRefactoringCommand() {
  }

  /**
   * Runs {@code plan} against {@code document}. A plan with no edits is not
   * executed at all, so replacing an owner with itself leaves no command on the
   * Undo stack.
   *
   * @param file the CODEOWNERS PSI file, used to scope the undo to that file
   */
  public static void execute(@NotNull Project project,
                             @Nullable PsiFile file,
                             @NotNull Document document,
                             @NotNull Plan plan) {
    if (plan.isNoOp() || !stillApplies(document, plan)) return;

    List<Edit> edits = plan.edits();
    WriteCommandAction.writeCommandAction(project, file == null ? PsiFile.EMPTY_ARRAY : new PsiFile[]{file})
            .withName(COMMAND_NAME)
            .run(() -> {
              for (int i = edits.size() - 1; i >= 0; i--) {
                Edit edit = edits.get(i);
                document.replaceString(edit.startOffset(), edit.endOffset(), edit.replacement());
              }
              PsiDocumentManager.getInstance(project).commitDocument(document);
            });
  }

  /**
   * Whether the document still holds what the plan was built from: every range
   * in bounds, and every range still covering the owner the plan expects there.
   * A plan is applied all or not at all, so a document that changed underneath
   * (which the modal dialog makes unlikely, but not impossible) cannot be left
   * half-refactored.
   */
  private static boolean stillApplies(@NotNull Document document, @NotNull Plan plan) {
    CharSequence text = document.getCharsSequence();
    for (Edit edit : plan.edits()) {
      if (edit.startOffset() < 0 || edit.endOffset() > text.length()) return false;
      String covered = text.subSequence(edit.startOffset(), edit.endOffset()).toString().trim();
      // Replacements cover the owner being renamed; deletions cover a duplicate,
      // which is either that same owner or one already spelled like the new one.
      if (!covered.equals(plan.currentOwner()) && !covered.equals(plan.newOwner())) return false;
    }
    return true;
  }
}
