package net.wolfig.codeowls.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges the {@code lang.syntaxHighlighterFactory} extension point in
 * {@code plugin.xml} to {@link CodeownersSyntaxHighlighter}.
 *
 * <p>The factory ignores the project and virtual-file arguments because
 * CODEOWNERS highlighting is purely stateless and content-independent — a fresh
 * {@link CodeownersSyntaxHighlighter} is returned on every call.
 */
public class CodeownersSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  @Override
  public @NotNull SyntaxHighlighter getSyntaxHighlighter(
          @Nullable Project project,
          @Nullable VirtualFile virtualFile) {
    return new CodeownersSyntaxHighlighter();
  }
}
