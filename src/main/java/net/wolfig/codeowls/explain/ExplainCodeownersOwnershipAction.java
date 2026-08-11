package net.wolfig.codeowls.explain;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.AppExecutorUtil;
import net.wolfig.codeowls.statusbar.CodeownersService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * The single file the action targets, or {@code null} when the selection is
   * not a single file (e.g. nothing selected, or a multi-selection).
   */
  private static @Nullable VirtualFile targetFile(@NotNull AnActionEvent e) {
    VirtualFile[] many = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (many != null && many.length > 1) return null;
    return e.getData(CommonDataKeys.VIRTUAL_FILE);
  }

  private static boolean isExplainable(@Nullable VirtualFile file) {
    return file != null && file.isValid() && !file.isDirectory()
            && !"CODEOWNERS".equals(file.getName());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && isExplainable(targetFile(e)));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile file = targetFile(e);
    if (project == null || !isExplainable(file)) return;

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
