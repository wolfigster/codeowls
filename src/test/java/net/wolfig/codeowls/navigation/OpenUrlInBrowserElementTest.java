package net.wolfig.codeowls.navigation;

import com.intellij.psi.PsiFile;
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
 * Tests for {@link OpenUrlInBrowserElement} — the fake PSI element that
 * {@code CodeownersOwnerGotoDeclarationHandler} returns so Ctrl/Cmd+Click on an
 * owner opens a URL instead of moving the caret.
 *
 * <p>These pin the properties that make GotoDeclaration take the browser path:
 * a {@code null} containing file and {@code canNavigate}/{@code canNavigateToSource}
 * flags. {@link OpenUrlInBrowserElement#navigate} itself is not exercised — it
 * would launch a real browser. Same-package so the package-private class is
 * visible.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class OpenUrlInBrowserElementTest {

  private static final String URL = "https://github.com/orgs/acme/teams/backend";

  private CodeInsightTestFixture fixture;
  private PsiFile parent;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
    parent = fixture.configureByText(CodeownersFileType.INSTANCE, "*.java @org/backend\n");
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  @Test
  public void getName_returnsTheUrl() {
    assertEquals(URL, new OpenUrlInBrowserElement(parent, URL).getName());
  }

  @Test
  public void getContainingFile_isNull_soGotoTakesTheNavigatablePath() {
    // A non-null file equal to the editor's would make GotoDeclaration move the
    // caret in-editor instead of opening the browser.
    assertNull(new OpenUrlInBrowserElement(parent, URL).getContainingFile());
  }

  @Test
  public void navigationFlags_navigableButNotToSource() {
    OpenUrlInBrowserElement element = new OpenUrlInBrowserElement(parent, URL);
    assertTrue(element.canNavigate());
    assertFalse(element.canNavigateToSource());
  }

  @Test
  public void getParentAndProject_delegateToTheParentElement() {
    OpenUrlInBrowserElement element = new OpenUrlInBrowserElement(parent, URL);
    assertSame(parent, element.getParent());
    assertSame(parent.getProject(), element.getProject());
  }
}
