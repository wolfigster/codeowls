package net.wolfig.codeowls.inlay;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Toggle CODEOWNERS file-count inlay hints from the editor / Find Action.
 *
 * <p>The provider is already user-toggleable through
 * <em>Settings | Editor | Inlay Hints | CODEOWNERS | File count</em> because
 * it's registered with {@code isEnabledByDefault="true"} and bundle keys for
 * a name/description. This action wraps the same persistent setting so users
 * can flip it from anywhere — particularly useful when the hints are getting
 * in the way and the user wants to silence them with a single keystroke
 * (Ctrl/Cmd+Shift+A → "Toggle CODEOWNERS file count hints").
 *
 * <p>After flipping the setting we restart the daemon so the inlays disappear
 * (or reappear) on the next pass without waiting for an unrelated reparse.
 */
public final class ToggleCodeownersFileCountHintsAction extends DumbAwareAction {

  /**
   * Must stay in sync with the {@code providerId} attribute in {@code plugin.xml}.
   */
  static final String PROVIDER_ID = "codeowls.fileCount";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DeclarativeInlayHintsSettings settings = DeclarativeInlayHintsSettings.Companion.getInstance();
    Boolean current = settings.isProviderEnabled(PROVIDER_ID);
    boolean next = !Boolean.TRUE.equals(current);
    settings.setProviderEnabled(PROVIDER_ID, next);
    Project project = e.getProject();
    if (project != null && !project.isDisposed()) {
      DaemonCodeAnalyzer.getInstance(project).restart("CODEOWNERS file count hints toggled");
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DeclarativeInlayHintsSettings settings = DeclarativeInlayHintsSettings.Companion.getInstance();
    boolean enabled = Boolean.TRUE.equals(settings.isProviderEnabled(PROVIDER_ID));
    e.getPresentation().setText(enabled
            ? "Disable CODEOWNERS File Count Hints"
            : "Enable CODEOWNERS File Count Hints");
    e.getPresentation().setDescription(enabled
            ? "Hide the file-count inlay hints in CODEOWNERS files"
            : "Show the file-count inlay hints in CODEOWNERS files");
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
