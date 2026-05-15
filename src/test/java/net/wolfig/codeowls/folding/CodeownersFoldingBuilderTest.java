package net.wolfig.codeowls.folding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersFoldingBuilder}.
 *
 * <p>Uses {@link CodeInsightTestFixture} so the builder runs against a real
 * {@code Document} and {@code PsiFile} produced for a CODEOWNERS file. The
 * helper {@link #fold(String)} drives the builder for the given file content
 * and returns the resulting descriptors.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersFoldingBuilderTest {

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

  private FoldingDescriptor[] fold(String content) {
    PsiFile psi = fixture.addFileToProject("CODEOWNERS", content);
    return ReadAction.compute(() -> {
      Document document = psi.getViewProvider().getDocument();
      assertNotNull(document);
      return new CodeownersFoldingBuilder().buildFoldRegions(psi, document, false);
    });
  }

  @Test
  public void buildFoldRegions_emptyFile_returnsNoDescriptors() {
    // Act
    FoldingDescriptor[] descriptors = fold("");

    // Assert
    assertEquals(0, descriptors.length);
  }

  @Test
  public void buildFoldRegions_noSections_returnsNoDescriptors() {
    // Arrange
    String content = """
            # top-level rules
            *.java @backend
            /src/** @core
            """;

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(0, descriptors.length);
  }

  @Test
  public void buildFoldRegions_singleSectionWithBody_foldsFromHeaderToEof() {
    // Arrange
    String content = """
            [Backend]
            *.java @backend
            /src/** @core
            """;

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals(0, descriptors[0].getRange().getStartOffset());
    assertEquals(content.length() - 1, descriptors[0].getRange().getEndOffset());
    assertEquals("[Backend]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_twoSections_foldsEachUpToNextHeader() {
    // Arrange
    String first = "[Backend]\n*.java @backend\n";
    String second = "[Frontend]\n*.ts @frontend\n";
    String content = first + second;

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(2, descriptors.length);
    assertEquals(0, descriptors[0].getRange().getStartOffset());
    assertEquals(first.length() - 1, descriptors[0].getRange().getEndOffset());
    assertEquals("[Backend]", descriptors[0].getPlaceholderText());
    assertEquals(first.length(), descriptors[1].getRange().getStartOffset());
    assertEquals(content.length() - 1, descriptors[1].getRange().getEndOffset());
    assertEquals("[Frontend]", descriptors[1].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_optionalSectionPrefix_usesFullHeaderAsPlaceholder() {
    // Arrange
    String content = "^[Backend]\n*.java @backend\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals("^[Backend]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_sectionWithApprovalCount_usesFullHeaderAsPlaceholder() {
    // Arrange
    String content = "[Backend][2]\n*.java @backend\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals("[Backend][2]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_unclosedBracket_doesNotFold() {
    // Arrange
    String content = "[Backend\n*.java @backend\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(0, descriptors.length);
  }

  @Test
  public void buildFoldRegions_sectionHeaderOnLastLine_doesNotFold() {
    // Arrange — no body lines after the header, so nothing to hide.
    String content = "*.java @backend\n[Backend]";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(0, descriptors.length);
  }

  @Test
  public void buildFoldRegions_leadingWhitespaceBeforeHeader_stillFolds() {
    // Arrange
    String content = "  [Backend]\n*.java @backend\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals("[Backend]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_headerWithDefaultOwnersOnSameLine_foldsAndUsesHeaderOnlyAsPlaceholder() {
    // Arrange
    String content = "[Backend] @org/backend\n*.java @backend\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals("[Backend]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_sectionWithApprovalCountAndDefaultOwnersOnSameLine_foldsAndUsesHeaderOnlyAsPlaceholder() {
    // Arrange
    String content = "[Documentation][2] @peter\n*.md @docs\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals("[Documentation][2]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_optionalSectionWithApprovalCountAndDefaultOwnersOnSameLine_foldsAndUsesHeaderOnlyAsPlaceholder() {
    // Arrange
    String content = "^[Documentation][2] @peter\n*.md @docs\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals("^[Documentation][2]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_sectionHeaderWithInvalidTextAfterHeader_doesNotFold() {
    // Arrange
    String content = "[Backend]invalid\n*.java @backend\n";

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(0, descriptors.length);
  }

  @Test
  public void buildFoldRegions_emptyLineInBody_endsFoldBeforeEmptyLine() {
    // Arrange — the empty line must terminate the section; the loose rule below
    // it is not part of the [Backend] fold.
    String content = """
            [Backend]
            /src/backend/** @backend-team
            /src/api/** @api-team
            
            *.jave @test-team
            """;
    int expectedEnd = content.indexOf("\n\n");

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals(0, descriptors[0].getRange().getStartOffset());
    assertEquals(expectedEnd, descriptors[0].getRange().getEndOffset());
    assertEquals("[Backend]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_blankLineWithWhitespace_endsFoldBeforeBlankLine() {
    // Arrange — a line containing only spaces/tabs must terminate the section
    // exactly like a fully empty line.
    String content = "[Backend]\n/src/backend @x\n   \t  \n*.jave @test\n";
    int expectedEnd = content.indexOf("\n   \t");

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(1, descriptors.length);
    assertEquals(0, descriptors[0].getRange().getStartOffset());
    assertEquals(expectedEnd, descriptors[0].getRange().getEndOffset());
    assertEquals("[Backend]", descriptors[0].getPlaceholderText());
  }

  @Test
  public void buildFoldRegions_sectionsSeparatedByEmptyLine_foldsBothIndependently() {
    // Arrange
    String content = """
            [Backend]
            /src/backend/** @backend-team
            
            [Frontend]
            /src/frontend/** @frontend-team
            """;
    int firstEnd = content.indexOf("\n\n");
    int secondStart = content.indexOf("[Frontend]");
    int secondEnd = content.length() - 1;

    // Act
    FoldingDescriptor[] descriptors = fold(content);

    // Assert
    assertEquals(2, descriptors.length);
    assertEquals(0, descriptors[0].getRange().getStartOffset());
    assertEquals(firstEnd, descriptors[0].getRange().getEndOffset());
    assertEquals("[Backend]", descriptors[0].getPlaceholderText());
    assertEquals(secondStart, descriptors[1].getRange().getStartOffset());
    assertEquals(secondEnd, descriptors[1].getRange().getEndOffset());
    assertEquals("[Frontend]", descriptors[1].getPlaceholderText());
  }

  @Test
  public void isCollapsedByDefault_anyNode_returnsFalse() {
    // Arrange
    String content = "[Backend]\n*.java @backend\n";
    FoldingDescriptor[] descriptors = fold(content);
    assertEquals(1, descriptors.length);

    // Act
    boolean collapsed = new CodeownersFoldingBuilder().isCollapsedByDefault(descriptors[0].getElement());

    // Assert
    assertFalse(collapsed);
  }
}
