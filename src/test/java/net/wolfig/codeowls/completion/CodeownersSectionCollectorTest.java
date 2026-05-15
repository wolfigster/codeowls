package net.wolfig.codeowls.completion;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CodeownersSectionCollector} — verifies section-header
 * recognition (including the optional {@code ^} prefix and trailing approval
 * counts) and the dedup / ordering contract relied on by
 * {@link CodeownersSectionCompletionProvider}.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersSectionCollectorTest {

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

  private PsiFile codeowners(String content) {
    return fixture.addFileToProject("CODEOWNERS", content);
  }

  @Test
  public void collect_emptyFile_returnsNoNames() {
    // Arrange
    PsiFile file = codeowners("");

    // Act
    List<String> names = ReadAction.compute(() -> CodeownersSectionCollector.collect(file));

    // Assert
    assertTrue(names.isEmpty());
  }

  @Test
  public void collect_severalSectionHeaders_returnsUniqueNamesInDocumentOrder() {
    // Arrange
    PsiFile file = codeowners(
            "[Backend]\n"
                    + "*.java @backend\n"
                    + "[Frontend]\n"
                    + "*.ts @frontend\n"
                    + "[Backend]\n"  // duplicate, must be deduped
                    + "*.kt @kotlin\n");

    // Act
    List<String> names = ReadAction.compute(() -> CodeownersSectionCollector.collect(file));

    // Assert
    assertEquals(List.of("Backend", "Frontend"), names);
  }

  @Test
  public void collect_optionalSectionHeader_returnsName() {
    // Arrange
    PsiFile file = codeowners("^[Documentation]\n*.md @docs\n");

    // Act
    List<String> names = ReadAction.compute(() -> CodeownersSectionCollector.collect(file));

    // Assert
    assertEquals(List.of("Documentation"), names);
  }

  @Test
  public void collect_sectionHeaderWithApprovalCountAndOwners_returnsOnlyName() {
    // Arrange
    PsiFile file = codeowners("[Backend][2] @org/backend @alice\n");

    // Act
    List<String> names = ReadAction.compute(() -> CodeownersSectionCollector.collect(file));

    // Assert
    assertEquals(List.of("Backend"), names);
  }

  @Test
  public void collect_unclosedBracket_isIgnored() {
    // Arrange
    PsiFile file = codeowners("[Backend\n*.java @backend\n");

    // Act
    List<String> names = ReadAction.compute(() -> CodeownersSectionCollector.collect(file));

    // Assert
    assertTrue(names.isEmpty());
  }

  @Test
  public void collect_commentStartingWithBracket_isIgnored() {
    // Arrange
    PsiFile file = codeowners("# [Not a section]\n[Backend]\n*.java @backend\n");

    // Act
    List<String> names = ReadAction.compute(() -> CodeownersSectionCollector.collect(file));

    // Assert
    assertEquals(List.of("Backend"), names);
  }
}
