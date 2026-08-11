package net.wolfig.codeowls.explain;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link ExplainCodeownersOwnershipAction}'s enablement logic — the
 * {@code update} contract that decides when "Explain Ownership" appears.
 *
 * <p>The action targets a single real file; it must hide for directories, for
 * the CODEOWNERS file itself, for an empty selection, and for a multi-file
 * selection. A {@link CodeInsightTestFixture} provides a real {@link Project}
 * and {@link VirtualFile}s, and enablement is probed with a synthetic
 * {@link TestActionEvent} over a {@link SimpleDataContext}.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class ExplainCodeownersOwnershipActionTest {

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

  private Project project() {
    return fixture.getProject();
  }

  /**
   * Runs the action's {@code update} over the given context and reports whether
   * the menu item would be shown. Wrapped in a read action because {@code update}
   * inspects VFS state.
   */
  private boolean isEnabled(DataContext context) {
    ExplainCodeownersOwnershipAction action = new ExplainCodeownersOwnershipAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action, context);
    return ReadAction.compute(() -> {
      action.update(event);
      return event.getPresentation().isEnabledAndVisible();
    });
  }

  @Test
  public void update_regularFile_isEnabled() {
    // Arrange
    VirtualFile file = fixture.addFileToProject("src/Foo.java", "").getVirtualFile();
    DataContext ctx = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project())
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build();

    // Act / Assert
    assertTrue(isEnabled(ctx));
  }

  @Test
  public void update_directory_isDisabled() {
    // Arrange — the parent directory of a project file.
    VirtualFile dir = fixture.addFileToProject("src/Foo.java", "").getVirtualFile().getParent();
    DataContext ctx = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project())
            .add(CommonDataKeys.VIRTUAL_FILE, dir)
            .build();

    // Act / Assert
    assertFalse(isEnabled(ctx));
  }

  @Test
  public void update_codeownersFileItself_isDisabled() {
    // Arrange — explaining a CODEOWNERS file's own ownership is not offered.
    VirtualFile codeowners = fixture.addFileToProject(".github/CODEOWNERS", "* @team\n").getVirtualFile();
    DataContext ctx = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project())
            .add(CommonDataKeys.VIRTUAL_FILE, codeowners)
            .build();

    // Act / Assert
    assertFalse(isEnabled(ctx));
  }

  @Test
  public void update_noFileSelected_isDisabled() {
    // Arrange — a project but no VIRTUAL_FILE in the context.
    DataContext ctx = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project())
            .build();

    // Act / Assert
    assertFalse(isEnabled(ctx));
  }

  @Test
  public void update_multipleFilesSelected_isDisabled() {
    // Arrange — a multi-file selection has no single target to explain.
    VirtualFile a = fixture.addFileToProject("src/A.java", "").getVirtualFile();
    VirtualFile b = fixture.addFileToProject("src/B.java", "").getVirtualFile();
    DataContext ctx = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project())
            .add(CommonDataKeys.VIRTUAL_FILE, a)
            .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{a, b})
            .build();

    // Act / Assert
    assertFalse(isEnabled(ctx));
  }

  @Test
  public void getActionUpdateThread_isBackgroundThread() {
    assertEquals(ActionUpdateThread.BGT,
            new ExplainCodeownersOwnershipAction().getActionUpdateThread());
  }
}
