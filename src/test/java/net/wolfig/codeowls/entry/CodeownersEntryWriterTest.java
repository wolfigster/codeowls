package net.wolfig.codeowls.entry;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import net.wolfig.codeowls.statusbar.CodeownersOwnerResolution;
import net.wolfig.codeowls.statusbar.CodeownersService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests formatting-preserving insertion and resolver integration.
 */
public class CodeownersEntryWriterTest {

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

  @Test
  public void appendRuleText_withoutEofNewline_preservesExistingText() {
    assertEquals("/src/** @developers\n/src/App.java @alice\n",
            CodeownersEntryWriter.appendRuleText(
                    "/src/** @developers", "/src/App.java @alice"));
  }

  @Test
  public void appendRuleText_withEofNewline_doesNotAddBlankLine() {
    assertEquals("/src/** @developers\n/src/App.java @alice\n",
            CodeownersEntryWriter.appendRuleText(
                    "/src/** @developers\n", "/src/App.java @alice"));
  }

  @Test
  public void appendRuleText_preservesBlankLinesAndCrLf() {
    assertEquals("# heading\r\n\r\n/src/** @developers\r\n/src/App.java @alice\r\n",
            CodeownersEntryWriter.appendRuleText(
                    "# heading\r\n\r\n/src/** @developers\r\n", "/src/App.java @alice"));
  }

  @Test
  public void appendAndNavigate_exactRuleFollowsBroaderRuleAndBecomesEffective() {
    VirtualFile codeowners = fixture.addFileToProject(
            ".github/CODEOWNERS", "/src/** @developers\n").getVirtualFile();
    VirtualFile target = fixture.addFileToProject("src/App.java", "").getVirtualFile();

    EdtTestUtil.runInEdtAndWait(() ->
            CodeownersEntryWriter.appendAndNavigate(
                    project(), codeowners, "/src/App.java @alice"));

    Document document = ReadAction.compute(() ->
            FileDocumentManager.getInstance().getDocument(codeowners));
    assertNotNull(document);
    assertEquals("/src/** @developers\n/src/App.java @alice\n", document.getText());
    CodeownersOwnerResolution resolution = ReadAction.compute(() ->
            CodeownersService.getInstance(project()).resolveOwners(target));
    assertEquals(List.of("@alice"), resolution.owners());
    assertEquals("/src/App.java", resolution.rule().pattern());
  }

  @Test
  public void appendAndNavigate_broaderRuleDoesNotBlockNewExactRule() {
    VirtualFile codeowners = fixture.addFileToProject(
            ".github/CODEOWNERS", "*.java @java\n/src/** @src\n").getVirtualFile();

    EdtTestUtil.runInEdtAndWait(() ->
            CodeownersEntryWriter.appendAndNavigate(
                    project(), codeowners, "/src/App.java @alice"));

    Document document = ReadAction.compute(() ->
            FileDocumentManager.getInstance().getDocument(codeowners));
    assertNotNull(document);
    assertTrue(document.getText().endsWith("/src/App.java @alice\n"));
  }

  @Test
  public void replaceAndNavigate_existingExactRule_changesOnlyThatRule() {
    VirtualFile codeowners = fixture.addFileToProject(
            ".github/CODEOWNERS",
            "# heading\n/src/** @developers\n/src/App.java   @old\n\n").getVirtualFile();
    VirtualFile target = fixture.addFileToProject("src/App.java", "").getVirtualFile();

    EdtTestUtil.runInEdtAndWait(() ->
            CodeownersEntryWriter.replaceAndNavigate(
                    project(), codeowners, "/src/App.java @new", 2));

    Document document = ReadAction.compute(() ->
            FileDocumentManager.getInstance().getDocument(codeowners));
    assertNotNull(document);
    assertEquals(
            "# heading\n/src/** @developers\n/src/App.java @new\n\n",
            document.getText());
    CodeownersOwnerResolution resolution = ReadAction.compute(() ->
            CodeownersService.getInstance(project()).resolveOwners(target));
    assertEquals(List.of("@new"), resolution.owners());
  }

  @Test
  public void appendAndNavigate_gitLabSectionFile_appendsAtEofWithExplicitOwners() {
    VirtualFile codeowners = fixture.addFileToProject(
            ".gitlab/CODEOWNERS", "[Backend][2] @backend\n/src/**\n").getVirtualFile();
    VirtualFile target = fixture.addFileToProject("src/App.java", "").getVirtualFile();

    EdtTestUtil.runInEdtAndWait(() ->
            CodeownersEntryWriter.appendAndNavigate(
                    project(), codeowners, "/src/App.java @alice"));

    CodeownersOwnerResolution resolution = ReadAction.compute(() ->
            CodeownersService.getInstance(project()).resolveOwners(target));
    assertEquals(List.of("@alice"), resolution.owners());
    assertFalse(resolution.rule().ownersInherited());
    assertEquals(Integer.valueOf(2), resolution.rule().approvalCount());
  }
}
