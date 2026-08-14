package net.wolfig.codeowls.entry;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import net.wolfig.codeowls.action.CodeowlsFileActionTarget;
import net.wolfig.codeowls.entry.CodeownersEntryRule.PathMode;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.statusbar.CodeownersService;
import org.jetbrains.annotations.NotNull;

/**
 * Adds a user-entered ownership rule for the selected project file.
 */
public final class AddCodeownersEntryAction extends DumbAwareAction {

  private static void showDialog(@NotNull Project project,
                                 CodeownersService.FileContext context) {
    if (context == null) {
      Messages.showInfoMessage(project,
              "No CODEOWNERS file found for this project.",
              "Add CODEOWNERS Entry");
      return;
    }

    AddCodeownersEntryDialog dialog =
            new AddCodeownersEntryDialog(project, context.relativePath());
    if (!dialog.showAndGet()) return;
    String rule = dialog.rule();
    if (rule == null || !context.codeownersFile().isValid()) return;

    CodeownersRule existing = dialog.pathMode() == PathMode.EXACT
            ? CodeownersEntryRule.existingExactRule(context.rules(), dialog.exactPattern())
            : null;
    if (existing == null) {
      CodeownersEntryWriter.appendAndNavigate(project, context.codeownersFile(), rule);
      return;
    }

    String currentOwners = String.join(" ", existing.owners());
    String newOwners = rule.substring(rule.indexOf(' ') + 1);
    int choice = Messages.showDialog(project,
            "An exact CODEOWNERS rule already exists for this file.\n\n"
                    + "Current: " + currentOwners + "\n"
                    + "New: " + newOwners,
            "Existing CODEOWNERS Rule",
            new String[]{"Replace Existing Rule", "Add Anyway", "Cancel"},
            0,
            Messages.getWarningIcon());
    if (choice == 0) {
      CodeownersEntryWriter.replaceAndNavigate(
              project, context.codeownersFile(), rule, existing.lineNumber());
    } else if (choice == 1) {
      CodeownersEntryWriter.appendAndNavigate(project, context.codeownersFile(), rule);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabledAndVisible(
            CodeowlsFileActionTarget.projectFileFrom(event) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    VirtualFile target = CodeowlsFileActionTarget.projectFileFrom(event);
    if (project == null || target == null) return;

    ReadAction.nonBlocking(() -> CodeownersService.getInstance(project).fileContext(target))
            .expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState(),
                    context -> showDialog(project, context))
            .submit(AppExecutorUtil.getAppExecutorService());
  }
}
