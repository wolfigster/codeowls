package net.wolfig.codeowls.explain;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.AppExecutorUtil;
import net.wolfig.codeowls.action.CodeowlsFileActionTarget;
import net.wolfig.codeowls.statusbar.CodeownersService;
import org.jetbrains.annotations.NotNull;

/**
 * Action that explains <em>why</em> the selected project file has its effective
 * CODEOWNERS owner(s): shows every matching rule in evaluation order, the
 * winning rule, section inheritance, and approval counts, with click-to-navigate
 * to each rule.
 *
 * <p>Registered on the Project View and Editor context menus (and reachable from
 * Find Action). Enabled only for a single real file — not a directory, and not a
 * CODEOWNERS file itself, since explaining a CODEOWNERS file's own ownership is
 * rarely what the user wants and the status-bar widget already covers the open
 * editor.
 *
 * <p>The ownership computation reuses {@link CodeownersService#explain} on a
 * non-blocking background read action — exactly as the status-bar widget does —
 * so the EDT never parses or matches. The popup is then shown on the EDT.
 */
public final class ExplainCodeownersOwnershipAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(CodeowlsFileActionTarget.from(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile file = CodeowlsFileActionTarget.from(e);
    if (project == null || file == null) return;

    // Resolve the popup anchor now, on the EDT, rather than retaining the
    // DataContext across the background computation.
    RelativePoint where = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());

    ReadAction.nonBlocking(() -> CodeownersService.getInstance(project).explain(file))
            .expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState(),
                    explanation -> OwnershipExplanationPopup.show(project, explanation, where))
            .submit(AppExecutorUtil.getAppExecutorService());
  }
}
