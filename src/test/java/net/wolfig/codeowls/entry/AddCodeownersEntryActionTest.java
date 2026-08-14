package net.wolfig.codeowls.entry;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests project-file eligibility for the context-menu action.
 */
public class AddCodeownersEntryActionTest {

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

  private boolean enabled(DataContext context) {
    AddCodeownersEntryAction action = new AddCodeownersEntryAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action, context);
    return ReadAction.compute(() -> {
      action.update(event);
      return event.getPresentation().isEnabledAndVisible();
    });
  }

  private DataContext context(VirtualFile file) {
    return SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, fixture.getProject())
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build();
  }

  @Test
  public void update_directory_isDisabled() {
    VirtualFile directory =
            fixture.addFileToProject("src/App.java", "").getVirtualFile().getParent();
    assertFalse(enabled(context(directory)));
  }

  @Test
  public void update_codeownersFile_isDisabled() {
    assertFalse(enabled(context(
            fixture.addFileToProject("CODEOWNERS", "* @all\n").getVirtualFile())));
  }

  @Test
  public void update_multipleFiles_isDisabled() {
    VirtualFile first = fixture.addFileToProject("src/A.java", "").getVirtualFile();
    VirtualFile second = fixture.addFileToProject("src/B.java", "").getVirtualFile();
    DataContext context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, fixture.getProject())
            .add(CommonDataKeys.VIRTUAL_FILE, first)
            .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{first, second})
            .build();

    assertFalse(enabled(context));
  }

  @Test
  public void getActionUpdateThread_isBackground() {
    assertEquals(ActionUpdateThread.BGT,
            new AddCodeownersEntryAction().getActionUpdateThread());
  }
}
