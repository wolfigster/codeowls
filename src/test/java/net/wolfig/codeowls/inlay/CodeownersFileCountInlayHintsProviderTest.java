package net.wolfig.codeowls.inlay;

import com.intellij.codeInsight.hints.declarative.InlayHintsCollector;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import net.wolfig.codeowls.lang.CodeownersFileType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link CodeownersFileCountInlayHintsProvider#createCollector} — the
 * language gate that decides whether the provider participates for a given
 * file. The provider must only attach to CODEOWNERS files and stay inert
 * everywhere else.
 *
 * <p>The provider's pure helpers ({@code formatHintText},
 * {@code patternEndOffsetInLine}) and the per-line counting are covered by
 * {@link CodeownersMatchCounterTest}; this class adds the editor-bound surface
 * that needs a real {@code PsiFile} / {@code Editor}.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersFileCountInlayHintsProviderTest {

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

  @Test
  public void createCollector_codeownersFile_returnsCollector() {
    // Arrange
    fixture.configureByText(CodeownersFileType.INSTANCE, "*.java @alice\n");

    // Act
    InlayHintsCollector collector = ReadAction.compute(() ->
            new CodeownersFileCountInlayHintsProvider()
                    .createCollector(fixture.getFile(), fixture.getEditor()));

    // Assert
    assertNotNull(collector);
  }

  @Test
  public void createCollector_nonCodeownersFile_returnsNull() {
    // Arrange — same content, but a plain-text file is not CODEOWNERS.
    fixture.configureByText("notes.txt", "*.java @alice\n");

    // Act
    InlayHintsCollector collector = ReadAction.compute(() ->
            new CodeownersFileCountInlayHintsProvider()
                    .createCollector(fixture.getFile(), fixture.getEditor()));

    // Assert
    assertNull(collector);
  }
}
