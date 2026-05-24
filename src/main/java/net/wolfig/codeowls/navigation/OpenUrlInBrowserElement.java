package net.wolfig.codeowls.navigation;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Non-physical navigation target whose {@link #navigate} opens a URL in the
 * external browser. Returned from {@link CodeownersOwnerGotoDeclarationHandler}
 * so that Ctrl+Click on an owner behaves like following a hyperlink.
 */
final class OpenUrlInBrowserElement extends FakePsiElement {

  private final PsiElement parent;
  private final String url;

  OpenUrlInBrowserElement(@NotNull PsiElement parent, @NotNull String url) {
    this.parent = parent;
    this.url = url;
  }

  @Override
  public PsiElement getParent() {
    return parent;
  }

  @Override
  public @NotNull Project getProject() {
    return parent.getProject();
  }

  @Override
  public @Nullable PsiFile getContainingFile() {
    // Intentionally null: a non-null file equal to the editor's would make
    // GotoDeclaration move the caret in-editor instead of taking the
    // Navigatable#navigate path that opens the browser.
    return null;
  }

  @Override
  public String getName() {
    return url;
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void navigate(boolean requestFocus) {
    BrowserUtil.browse(url);
  }
}
