package net.wolfig.codeowls.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.statusbar.CodeownersService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps CODEOWNERS rules in sync when a file or directory is moved or renamed.
 *
 * <p>When refactoring starts on a file/directory, the item's repo-relative path
 * is captured; after the refactoring, any rule in the governing CODEOWNERS file
 * whose pattern targets that path (or a path under a moved directory) is
 * rewritten to the new location (see {@link CodeownersPathRewriter}). Both the
 * old and new paths are resolved against the same CODEOWNERS file via
 * {@link CodeownersService#fileContext}; if the move crosses into a different
 * CODEOWNERS file's scope, nothing is changed (too ambiguous to rewrite safely).
 */
public final class CodeownersMoveRenameListenerProvider implements RefactoringElementListenerProvider {

  private static void applyRewrites(@NotNull Project project, @NotNull VirtualFile codeownersFile,
                                    @NotNull List<CodeownersRule> rules,
                                    @NotNull String oldPath, @NotNull String newPath) {
    List<Rewrite> rewrites = new ArrayList<>();
    for (CodeownersRule rule : rules) {
      String rewritten = CodeownersPathRewriter.rewritePattern(rule.pattern(), oldPath, newPath);
      if (rewritten != null && !rewritten.equals(rule.pattern())) {
        rewrites.add(new Rewrite(rule.lineNumber(), rule.pattern(), rewritten));
      }
    }
    if (rewrites.isEmpty()) return;
    // Edit highest line first so earlier edits don't shift later offsets.
    rewrites.sort(Comparator.comparingInt(Rewrite::line).reversed());

    WriteCommandAction.runWriteCommandAction(project, "Update CODEOWNERS Paths", null, () -> {
      Document document = FileDocumentManager.getInstance().getDocument(codeownersFile);
      if (document == null) return;
      for (Rewrite rewrite : rewrites) {
        if (rewrite.line() < 0 || rewrite.line() >= document.getLineCount()) continue;
        int lineStart = document.getLineStartOffset(rewrite.line());
        int lineEnd = document.getLineEndOffset(rewrite.line());
        CharSequence text = document.getCharsSequence();
        int patternStart = lineStart;
        while (patternStart < lineEnd
                && (text.charAt(patternStart) == ' ' || text.charAt(patternStart) == '\t')) {
          patternStart++;
        }
        int patternEnd = patternStart + rewrite.oldPattern().length();
        // Defensive: only replace if the text really is the old pattern.
        if (patternEnd > lineEnd
                || !text.subSequence(patternStart, patternEnd).toString().equals(rewrite.oldPattern())) {
          continue;
        }
        document.replaceString(patternStart, patternEnd, rewrite.newPattern());
      }
      FileDocumentManager.getInstance().saveDocument(document);
    });
  }

  @Override
  public @Nullable RefactoringElementListener getListener(@NotNull PsiElement element) {
    if (!(element instanceof PsiFileSystemItem item)) return null;
    VirtualFile vf = item.getVirtualFile();
    if (vf == null || !vf.isValid()) return null;
    Project project = item.getProject();
    // Don't react to the CODEOWNERS file moving — we only rewrite rules that
    // point at other files.
    if (project.isDisposed() || "CODEOWNERS".equals(vf.getName())) return null;

    CodeownersService.FileContext ctx =
            ReadAction.compute(() -> CodeownersService.getInstance(project).fileContext(vf));
    if (ctx == null) return null;
    return new Listener(project, ctx.codeownersFile(), ctx.relativePath());
  }

  private static final class Listener implements RefactoringElementListener {

    private final Project project;
    private final VirtualFile codeownersFile;
    private final String oldPath;

    Listener(@NotNull Project project, @NotNull VirtualFile codeownersFile, @NotNull String oldPath) {
      this.project = project;
      this.codeownersFile = codeownersFile;
      this.oldPath = oldPath;
    }

    @Override
    public void elementMoved(@NotNull PsiElement newElement) {
      sync(newElement);
    }

    @Override
    public void elementRenamed(@NotNull PsiElement newElement) {
      sync(newElement);
    }

    private void sync(@NotNull PsiElement newElement) {
      if (project.isDisposed() || !(newElement instanceof PsiFileSystemItem item)) return;
      VirtualFile newVf = item.getVirtualFile();
      if (newVf == null || !newVf.isValid()) return;

      CodeownersService.FileContext ctx =
              ReadAction.compute(() -> CodeownersService.getInstance(project).fileContext(newVf));
      // Only rewrite when the same CODEOWNERS file still governs the item.
      if (ctx == null || !codeownersFile.equals(ctx.codeownersFile())) return;
      if (oldPath.equals(ctx.relativePath())) return;

      applyRewrites(project, codeownersFile, ctx.rules(), oldPath, ctx.relativePath());
    }
  }

  private record Rewrite(int line, @NotNull String oldPattern, @NotNull String newPattern) {
  }
}
