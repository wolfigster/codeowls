package net.wolfig.codeowls.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

/**
 * The "Codeowls" submenu that groups the plugin's context-menu actions.
 *
 * <p>Hides itself when none of its actions are visible, so contexts where no
 * Codeowls action applies (directories, the CODEOWNERS file itself, non-file
 * selections) don't show an empty "Codeowls" submenu.
 */
public final class CodeowlsActionGroup extends DefaultActionGroup {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
