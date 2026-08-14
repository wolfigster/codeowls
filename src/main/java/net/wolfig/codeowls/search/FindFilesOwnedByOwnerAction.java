package net.wolfig.codeowls.search;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import net.wolfig.codeowls.action.CodeownersOwnerActionTarget;
import net.wolfig.codeowls.search.OwnershipSearchService.OwnedFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Finds files whose last matching CODEOWNERS rule effectively includes the
 * owner token under the caret.
 */
public final class FindFilesOwnedByOwnerAction extends DumbAwareAction {

  static final String GENERIC_TEXT = "Find files owned by OWNER";

  private static PsiElement ownerTarget(@NotNull AnActionEvent event) {
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
    return CodeownersOwnerActionTarget.from(editor, file);
  }

  static void showResults(@NotNull Project project,
                          @NotNull String owner,
                          @NotNull List<OwnedFile> matches) {
    Usage[] usages = matches.stream()
            .map(match -> (Usage) new OwnedFileUsage(project, match))
            .toArray(Usage[]::new);

    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Files owned by " + owner);
    presentation.setTabName("Files owned by " + owner);
    presentation.setToolwindowTitle("Owned Files");
    presentation.setScopeText("Project");
    presentation.setSearchString(owner);
    presentation.setCodeUsages(false);
    presentation.setUsageTypeFilteringAvailable(false);
    presentation.setMergeDupLinesAvailable(false);

    UsageViewManager.getInstance(project)
            .showUsages(UsageTarget.EMPTY_ARRAY, usages, presentation);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    PsiElement owner = ownerTarget(event);
    boolean available = event.getProject() != null && owner != null;
    event.getPresentation().setEnabledAndVisible(available);
    event.getPresentation().setText(available
            ? "Find files owned by " + owner.getText()
            : GENERIC_TEXT);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    PsiElement ownerElement = ownerTarget(event);
    PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);
    if (project == null || ownerElement == null || psiFile == null) return;

    VirtualFile codeownersFile = psiFile.getVirtualFile();
    if (codeownersFile == null) return;
    String owner = ownerElement.getText();

    new Task.Backgroundable(project, "Finding files owned by " + owner, true) {
      private List<OwnedFile> matches = List.of();

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        matches = ReadAction.nonBlocking(() ->
                        OwnershipSearchService.getInstance(project)
                                .findFilesOwnedBy(owner, codeownersFile, indicator))
                .expireWith(project)
                .wrapProgress(indicator)
                .executeSynchronously();
      }

      @Override
      public void onSuccess() {
        if (matches.isEmpty()) {
          Messages.showInfoMessage(project,
                  "No files are effectively owned by " + owner + ".",
                  "Files owned by " + owner);
          return;
        }
        showResults(project, owner, matches);
      }
    }.queue();
  }
}
