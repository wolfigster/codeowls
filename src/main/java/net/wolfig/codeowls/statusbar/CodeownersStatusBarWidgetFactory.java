package net.wolfig.codeowls.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Builds {@link CodeownersStatusBarWidget} for each project window.
 *
 * <p>Registered as a {@code statusBarWidgetFactory} extension; the platform
 * handles installation and per-project lifecycle.
 */
public final class CodeownersStatusBarWidgetFactory implements StatusBarWidgetFactory {

  /**
   * Widget id used both as the factory id and as {@link StatusBarWidget#ID()}.
   * Persisted in the user's status bar configuration, so changing it would
   * silently hide the widget for existing users.
   */
  public static final String ID = "Codeowls.StatusBarWidget";

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @NotNull String getDisplayName() {
    return "CODEOWNERS";
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return true;
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new CodeownersStatusBarWidget(project);
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
    Disposer.dispose(widget);
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return true;
  }
}
