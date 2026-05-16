package net.wolfig.codeowls.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import org.jetbrains.annotations.NotNull;

public final class CodeownersTypedHandler extends TypedHandlerDelegate {

  /**
   * Opens the completion lookup automatically when the user types {@code @},
   * so owner suggestions appear without pressing Ctrl+Space first.
   */
  @Override
  public @NotNull Result checkAutoPopup(
          char charTyped,
          @NotNull Project project,
          @NotNull Editor editor,
          @NotNull PsiFile file
  ) {
    if (charTyped == '@' && file.getLanguage().is(CodeownersLanguage.INSTANCE)) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      return Result.STOP;
    }

    return Result.CONTINUE;
  }
}
