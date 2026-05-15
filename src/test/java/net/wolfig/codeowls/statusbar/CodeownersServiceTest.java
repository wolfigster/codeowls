package net.wolfig.codeowls.statusbar;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersService}.
 *
 * <p>Uses {@link CodeInsightTestFixture} so that {@link Project},
 * {@link VirtualFile}, and content-root resolution behave like in a real IDE.
 * The fixture is built manually in {@link #setUp()} and torn down in
 * {@link #tearDown()} — that lets these tests stay on plain JUnit 4 instead of
 * the JUnit 3-flavoured {@code BasePlatformTestCase}.
 *
 * <p>Each test builds the file layout via {@code fixture.addFileToProject} and
 * then exercises {@link CodeownersService#resolveOwners}.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersServiceTest {

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

  private CodeownersService service() {
    return CodeownersService.getInstance(project());
  }

  /**
   * Drives {@link CodeownersService#resolveOwners} from inside a read action,
   * mirroring how the status-bar widget calls it in production.
   */
  private CodeownersOwnerResolution resolveOwners(VirtualFile file) {
    return ReadAction.compute(() -> service().resolveOwners(file));
  }

  @Test
  public void resolveOwners_nullFile_returnsNone() {
    // Act
    CodeownersOwnerResolution res = resolveOwners(null);

    // Assert
    assertTrue(res.isEmpty());
  }

  @Test
  public void resolveOwners_noCodeownersFile_returnsNone() {
    // Arrange
    VirtualFile file = fixture.addFileToProject("src/Foo.java", "class Foo {}").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertTrue(res.isEmpty());
  }

  @Test
  public void resolveOwners_matchingRule_returnsOwnersAndRule() {
    // Arrange
    fixture.addFileToProject(".github/CODEOWNERS", "*.java @backend\n");
    VirtualFile file = fixture.addFileToProject("src/Foo.java", "class Foo {}").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertFalse(res.isEmpty());
    assertEquals(List.of("@backend"), res.owners());
    assertNotNull(res.rule());
    assertEquals("*.java", res.rule().pattern());
  }

  @Test
  public void resolveOwners_twoMatchingRules_returnsOwnersFromLastMatch() {
    // Arrange — both rules match src/Foo.java; the later one should win.
    fixture.addFileToProject(".github/CODEOWNERS",
            "*.java @first\nsrc/*.java @second\n");
    VirtualFile file = fixture.addFileToProject("src/Foo.java", "").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertEquals(List.of("@second"), res.owners());
  }

  @Test
  public void resolveOwners_dotGithubAndRootCodeowners_prefersDotGithub() {
    // Arrange — both files exist; .github/CODEOWNERS wins per GitHub precedence.
    fixture.addFileToProject(".github/CODEOWNERS", "* @github\n");
    fixture.addFileToProject("CODEOWNERS", "* @root\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertEquals(List.of("@github"), res.owners());
  }

  @Test
  public void resolveOwners_rootCodeownersOnly_usesRootCodeowners() {
    // Arrange
    fixture.addFileToProject("CODEOWNERS", "* @root\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertEquals(List.of("@root"), res.owners());
  }

  @Test
  public void resolveOwners_docsCodeownersOnly_usesDocsCodeowners() {
    // Arrange
    fixture.addFileToProject("docs/CODEOWNERS", "* @docs\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertEquals(List.of("@docs"), res.owners());
  }

  @Test
  public void resolveOwners_multipleOwnersOnRule_returnsAllOwners() {
    // Arrange
    fixture.addFileToProject(".github/CODEOWNERS",
            "*.java @backend-team @john alice@example.com\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertEquals(
            List.of("@backend-team", "@john", "alice@example.com"),
            res.owners());
  }

  @Test
  public void getCodeownersFile_afterResolve_returnsSourceFile() {
    // Arrange
    PsiFile codeowners = fixture.addFileToProject(".github/CODEOWNERS", "* @any\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();
    resolveOwners(file);

    // Act
    VirtualFile sourceFile = service().getCodeownersFile();

    // Assert
    assertEquals(codeowners.getVirtualFile(), sourceFile);
  }

  @Test
  public void resolveOwners_codeownersEditedInDocument_reflectsNewContentOnNextCall() {
    // Arrange — initial CODEOWNERS, first resolve seeds the cache.
    PsiFile codeowners = fixture.addFileToProject(".github/CODEOWNERS", "*.java @first\n");
    VirtualFile target = fixture.addFileToProject("Foo.java", "").getVirtualFile();
    assertEquals(List.of("@first"), resolveOwners(target).owners());
    Document doc = ReadAction.compute(() ->
            FileDocumentManager.getInstance().getDocument(codeowners.getVirtualFile()));
    assertNotNull(doc);

    // Act — edit the document (not the file on disk). The document modification
    // stamp changes, so the cache should be invalidated on next lookup.
    WriteCommandAction.runWriteCommandAction(project(), () -> doc.setText("*.java @updated\n"));

    // Assert
    assertEquals(List.of("@updated"), resolveOwners(target).owners());
  }

  @Test
  public void resolveOwners_fileNotMatchedByAnyRule_returnsNone() {
    // Arrange
    fixture.addFileToProject(".github/CODEOWNERS", "*.kt @kotlin\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();

    // Act
    CodeownersOwnerResolution res = resolveOwners(file);

    // Assert
    assertTrue(res.isEmpty());
  }
}
