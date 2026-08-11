package net.wolfig.codeowls.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersUnnecessaryRuleInspection} — the LocalInspectionTool
 * that turns {@link CodeownersRedundancyAnalyzer} findings into editor warnings
 * with a "remove rule" quick fix.
 *
 * <p>The analyzer's decision logic is covered by
 * {@code CodeownersRedundancyAnalyzerTest}; these tests cover the wiring the
 * wrapper adds: findings become problem descriptors with the right message and
 * the quick fix deletes the offending rule line. The inspection is driven
 * directly through {@link com.intellij.codeInspection.LocalInspectionTool#checkFile}
 * rather than the daemon, so the assertions don't depend on editor / highlighting
 * setup — only on the wrapper's own behavior. A real project tree is still
 * needed so the inspection can tell which patterns match a file; the matching
 * files are created as siblings of {@code CODEOWNERS} so they are reliably found
 * by the project-root walk.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersUnnecessaryRuleInspectionTest {

  private CodeInsightTestFixture fixture;

  private static List<String> messages(ProblemDescriptor[] problems) {
    return Arrays.stream(problems)
            .map(ProblemDescriptor::getDescriptionTemplate)
            .collect(Collectors.toList());
  }

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
   * Runs the inspection over {@code codeowners} and returns its problem
   * descriptors (never {@code null}), under a read action as the platform requires.
   */
  private ProblemDescriptor[] inspect(PsiFile codeowners) {
    return ReadAction.compute(() -> {
      InspectionManager manager = InspectionManager.getInstance(project());
      ProblemDescriptor[] problems =
              new CodeownersUnnecessaryRuleInspection().checkFile(codeowners, manager, true);
      return problems == null ? ProblemDescriptor.EMPTY_ARRAY : problems;
    });
  }

  @Test
  public void inspection_patternMatchingNoProjectFile_isFlagged() {
    // Arrange — no .kt file exists, so "*.kt" matches nothing.
    fixture.addFileToProject("Foo.java", "");
    PsiFile codeowners = fixture.addFileToProject("CODEOWNERS", "*.kt @kotlin\n");

    // Act
    ProblemDescriptor[] problems = inspect(codeowners);

    // Assert
    assertEquals("problems: " + messages(problems), 1, problems.length);
    assertTrue(problems[0].getDescriptionTemplate().contains("matches no files"));
  }

  @Test
  public void inspection_ruleShadowedByLaterIdenticalRule_isFlagged() {
    // Arrange — the first rule is fully shadowed by the identical later one; a
    // matching file exists so neither is a "no files match" case.
    fixture.addFileToProject("Foo.java", "");
    PsiFile codeowners = fixture.addFileToProject("CODEOWNERS", "*.java @first\n*.java @second\n");

    // Act
    ProblemDescriptor[] problems = inspect(codeowners);

    // Assert — only the earlier, shadowed rule is flagged.
    assertEquals("problems: " + messages(problems), 1, problems.length);
    assertTrue(problems[0].getDescriptionTemplate().contains("is shadowed by the rule on line 2"));
  }

  @Test
  public void inspection_allRulesUseful_producesNoFindings() {
    // Arrange — every rule matches a real file and none shadows another.
    fixture.addFileToProject("Foo.java", "");
    fixture.addFileToProject("Guide.md", "");
    PsiFile codeowners = fixture.addFileToProject("CODEOWNERS", "*.java @backend\n*.md @docs\n");

    // Act
    ProblemDescriptor[] problems = inspect(codeowners);

    // Assert
    assertEquals("no findings expected, got: " + messages(problems), 0, problems.length);
  }

  @Test
  public void quickFix_removesTheFlaggedRuleLine() {
    // Arrange — the unnecessary "*.kt" rule is flagged; "*.java" matches Foo.java.
    fixture.addFileToProject("Foo.java", "");
    PsiFile codeowners = fixture.addFileToProject("CODEOWNERS", "*.kt @kotlin\n*.java @backend\n");
    ProblemDescriptor[] problems = inspect(codeowners);
    assertEquals("problems: " + messages(problems), 1, problems.length);
    ProblemDescriptor flagged = problems[0];
    QuickFix<?>[] fixes = flagged.getFixes();
    assertNotNull("expected a quick fix", fixes);
    assertEquals(1, fixes.length);
    assertEquals("Remove CODEOWNERS rule", fixes[0].getFamilyName());

    // Act — apply the quick fix inside a write command, as the platform would.
    WriteCommandAction.runWriteCommandAction(project(), () ->
            ((LocalQuickFix) fixes[0]).applyFix(project(), flagged));

    // Assert — only the unnecessary "*.kt" line is gone.
    Document document = ReadAction.compute(() -> codeowners.getViewProvider().getDocument());
    assertNotNull(document);
    assertEquals("*.java @backend\n", document.getText());
  }
}
