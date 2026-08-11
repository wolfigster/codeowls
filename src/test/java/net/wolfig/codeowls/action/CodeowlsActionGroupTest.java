package net.wolfig.codeowls.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CodeowlsActionGroup}, the "Codeowls" context-menu submenu.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeowlsActionGroupTest {

  @Test
  public void isDefaultActionGroup() {
    assertTrue(new CodeowlsActionGroup() instanceof DefaultActionGroup);
  }

  @Test
  public void getActionUpdateThread_isBackground() {
    // The group computes child visibility off the EDT, matching its actions.
    assertEquals(ActionUpdateThread.BGT, new CodeowlsActionGroup().getActionUpdateThread());
  }
}
