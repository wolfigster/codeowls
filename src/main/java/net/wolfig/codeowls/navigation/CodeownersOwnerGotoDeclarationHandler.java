package net.wolfig.codeowls.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import org.jetbrains.annotations.Nullable;

/**
 * Makes user and team owners in a CODEOWNERS file Ctrl+Clickable: navigating to
 * an owner opens that user's or group's page on the project's Git hosting
 * platform (GitHub, GitLab, …) in the external browser.
 *
 * <p>The target URL is computed by {@link CodeownersRemoteService} from the
 * repository's Git remote. When the project has no Git remote, or the owner is
 * a role ({@code @@maintainer}) or an e-mail address, no target is offered and
 * the owner is left as plain text.
 *
 * <p>Registered as a global {@code gotoDeclarationHandler}; it self-filters by
 * matching only this language's owner token types, which are unique to
 * CODEOWNERS files, so it is inert in every other file.
 */
public final class CodeownersOwnerGotoDeclarationHandler implements GotoDeclarationHandler {

  @Override
  public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                           int offset,
                                                           @Nullable Editor editor) {
    if (sourceElement == null) return null;
    ASTNode node = sourceElement.getNode();
    if (node == null || !isOwner(node.getElementType())) return null;

    String owner = sourceElement.getText();
    if (owner == null || owner.isEmpty()) return null;

    Project project = sourceElement.getProject();
    String url = CodeownersRemoteService.getInstance(project).ownerUrl(owner);
    if (url == null) return null;

    return new PsiElement[]{new OpenUrlInBrowserElement(sourceElement, url)};
  }

  private static boolean isOwner(@Nullable IElementType type) {
    return type == CodeownersTokenTypes.USER_OWNER || type == CodeownersTokenTypes.TEAM_OWNER;
  }
}
