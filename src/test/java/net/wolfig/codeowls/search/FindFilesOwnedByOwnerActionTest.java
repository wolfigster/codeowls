package net.wolfig.codeowls.search;

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
 * Tests that the action is offered only for real lexer-classified owner tokens.
 */
public class FindFilesOwnedByOwnerActionTest {

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

  private AnActionEvent updateAt(String content) {
    fixture.configureByText(CodeownersFileType.INSTANCE, content);
    DataContext context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, fixture.getProject())
            .add(CommonDataKeys.EDITOR, fixture.getEditor())
            .add(CommonDataKeys.PSI_FILE, fixture.getFile())
            .build();
    FindFilesOwnedByOwnerAction action = new FindFilesOwnedByOwnerAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action, context);
    ReadAction.run(() -> action.update(event));
    return event;
  }

  @Test
  public void update_allSupportedOwnerTokens_areEnabledWithDynamicText() {
    AnActionEvent user = updateAt("/src/** @al<caret>ice\n");
    assertTrue(user.getPresentation().isEnabledAndVisible());
    assertEquals("Find files owned by @alice", user.getPresentation().getText());

    assertTrue(updateAt("/src/** @org/<caret>team\n").getPresentation().isEnabledAndVisible());
    assertTrue(updateAt("/src/** alice@exa<caret>mple.com\n").getPresentation().isEnabledAndVisible());
    assertTrue(updateAt("/src/** @@main<caret>tainer\n").getPresentation().isEnabledAndVisible());
    assertTrue(updateAt("[Backend] @back<caret>end\n/src/**\n")
            .getPresentation().isEnabledAndVisible());
  }

  @Test
  public void update_nonOwnerTokens_areDisabled() {
    assertFalse(updateAt("/src<caret>/** @alice\n").getPresentation().isEnabledAndVisible());
    assertFalse(updateAt("# @al<caret>ice\n").getPresentation().isEnabledAndVisible());
    assertFalse(updateAt("[Back<caret>end] @alice\n").getPresentation().isEnabledAndVisible());
    assertFalse(updateAt("[Backend][<caret>2] @alice\n").getPresentation().isEnabledAndVisible());
    assertFalse(updateAt("/src/** <caret> @alice\n").getPresentation().isEnabledAndVisible());
    assertFalse(updateAt("/src/** hel<caret>lo\n").getPresentation().isEnabledAndVisible());
  }

  @Test
  public void update_nonCodeownersFile_isDisabled() {
    fixture.configureByText(PlainTextFileType.INSTANCE, "/src/** @al<caret>ice\n");
    DataContext context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, fixture.getProject())
            .add(CommonDataKeys.EDITOR, fixture.getEditor())
            .add(CommonDataKeys.PSI_FILE, fixture.getFile())
            .build();
    FindFilesOwnedByOwnerAction action = new FindFilesOwnedByOwnerAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action, context);

    ReadAction.run(() -> action.update(event));

    assertFalse(event.getPresentation().isEnabledAndVisible());
  }

  @Test
  public void getActionUpdateThread_isBackgroundThread() {
    assertEquals(ActionUpdateThread.BGT, new FindFilesOwnedByOwnerAction().getActionUpdateThread());
  }
}
