package net.wolfig.codeowls.refactoring;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import net.wolfig.codeowls.lang.CodeownersFileType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the enablement contract of "Refactor Owner…": it appears when — and
 * only when — the caret sits on a real CODEOWNERS owner token.
 *
 * <p>The caret is placed with the fixture's {@code <caret>} marker, and
 * enablement is probed with a synthetic {@link TestActionEvent}, exactly as the
 * IDE would call {@code update}.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class RefactorCodeownersOwnerActionTest {

  private CodeInsightTestFixture fixture;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  /**
   * Configures a CODEOWNERS file with the given {@code <caret>} position and
   * reports whether the action would be offered there.
   */
  private boolean isEnabledAtCaret(String codeownersContent) {
    fixture.configureByText(CodeownersFileType.INSTANCE, codeownersContent);
    return isEnabled();
  }

  private boolean isEnabled() {
    DataContext context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, fixture.getProject())
            .add(CommonDataKeys.EDITOR, fixture.getEditor())
            .add(CommonDataKeys.PSI_FILE, fixture.getFile())
            .build();
    RefactorCodeownersOwnerAction action = new RefactorCodeownersOwnerAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action, context);
    return ReadAction.compute(() -> {
      action.update(event);
      return event.getPresentation().isEnabledAndVisible();
    });
  }

  @Test
  public void update_caretOnUserOwner_isEnabled() {
    assertTrue(isEnabledAtCaret("/api/** @al<caret>ice\n"));
  }

  @Test
  public void update_caretOnTeamOwner_isEnabled() {
    assertTrue(isEnabledAtCaret("/api/** @org/<caret>backend\n"));
  }

  @Test
  public void update_caretOnRoleOwner_isEnabled() {
    assertTrue(isEnabledAtCaret("/api/** @@main<caret>tainer\n"));
  }

  @Test
  public void update_caretOnEmailOwner_isEnabled() {
    assertTrue(isEnabledAtCaret("/api/** alice@exa<caret>mple.com\n"));
  }

  @Test
  public void update_caretOnSectionDefaultOwner_isEnabled() {
    assertTrue(isEnabledAtCaret("[Backend][2] @al<caret>ice\n/api/**\n"));
  }

  @Test
  public void update_caretRightAfterOwner_isEnabled() {
    // Arrange / Act / Assert — the caret at the end of the token still counts.
    assertTrue(isEnabledAtCaret("/api/** @alice<caret>\n"));
  }

  @Test
  public void update_caretOnPattern_isDisabled() {
    assertFalse(isEnabledAtCaret("/api<caret>/** @alice\n"));
  }

  @Test
  public void update_caretOnComment_isDisabled() {
    assertFalse(isEnabledAtCaret("# ask @al<caret>ice first\n/api/** @alice\n"));
  }

  @Test
  public void update_caretOnSectionName_isDisabled() {
    assertFalse(isEnabledAtCaret("[Back<caret>end]\n/api/** @alice\n"));
  }

  @Test
  public void update_caretOnApprovalCount_isDisabled() {
    assertFalse(isEnabledAtCaret("[Backend][<caret>2] @alice\n/api/**\n"));
  }

  @Test
  public void update_caretOnWhitespace_isDisabled() {
    assertFalse(isEnabledAtCaret("/api/**\n<caret>\n/ui/** @alice\n"));
  }

  @Test
  public void update_caretOnArbitraryText_isDisabled() {
    // Arrange — "hello" in owner position is a bad character, not an owner.
    assertFalse(isEnabledAtCaret("/api/** hel<caret>lo\n"));
  }

  @Test
  public void update_nonCodeownersFile_isDisabled() {
    // Arrange — the same text in a plain-text file must not offer the action.
    fixture.configureByText(PlainTextFileType.INSTANCE, "/api/** @al<caret>ice\n");

    // Act / Assert
    assertFalse(isEnabled());
  }

  @Test
  public void update_noEditor_isDisabled() {
    // Arrange
    DataContext context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, fixture.getProject())
            .build();
    RefactorCodeownersOwnerAction action = new RefactorCodeownersOwnerAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action, context);

    // Act
    ReadAction.run(() -> action.update(event));

    // Assert
    assertFalse(event.getPresentation().isEnabledAndVisible());
  }

  @Test
  public void getActionUpdateThread_isBackgroundThread() {
    assertEquals(ActionUpdateThread.BGT, new RefactorCodeownersOwnerAction().getActionUpdateThread());
  }
}
