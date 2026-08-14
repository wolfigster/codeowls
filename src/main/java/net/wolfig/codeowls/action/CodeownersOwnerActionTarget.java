package net.wolfig.codeowls.action;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the CODEOWNERS owner token targeted by an editor action.
 */
public final class CodeownersOwnerActionTarget {

  private CodeownersOwnerActionTarget() {
  }

  /**
   * A caret immediately after an owner still targets that owner.
   * Must be called under a read action.
   */
  public static @Nullable PsiElement from(@Nullable Editor editor, @Nullable PsiFile file) {
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
}
