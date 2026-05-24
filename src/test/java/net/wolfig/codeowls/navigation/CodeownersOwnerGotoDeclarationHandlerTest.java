package net.wolfig.codeowls.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
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
 * Tests for {@link CodeownersOwnerGotoDeclarationHandler} — the Ctrl+Click
 * navigation surface. Drives the handler against a real CODEOWNERS file in a
 * {@link CodeInsightTestFixture} and seeds the project's remote via
 * {@link CodeownersRemoteService#setRemoteUrlForTesting} so the produced URL is
 * deterministic and no {@code .git/config} is touched.
 *
 * <p>The host/URL math is covered by {@link RemoteUrlResolverTest}; here we
 * verify token selection (which owners become navigable) and that the returned
 * target is an {@link OpenUrlInBrowserElement} bound to the right URL.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersOwnerGotoDeclarationHandlerTest {

  private CodeInsightTestFixture fixture;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
    // Isolate tests: start with no remote; positive cases opt in explicitly.
    remoteService().setRemoteUrlForTesting(null);
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      remoteService().setRemoteUrlForTesting(null);
      fixture.tearDown();
      fixture = null;
    }
  }

  private CodeownersRemoteService remoteService() {
    return CodeownersRemoteService.getInstance(fixture.getProject());
  }

  /**
   * Configures {@code content}, then runs the handler at the start of
   * {@code token} (the offset of its leading {@code @} / first char).
   */
  private PsiElement[] targetsAt(String content, String token) {
    fixture.configureByText(CodeownersFileType.INSTANCE, content);
    int offset = content.indexOf(token);
    assertTrue("token not found in content: " + token, offset >= 0);
    return ReadAction.compute(() -> {
      PsiElement element = fixture.getFile().findElementAt(offset);
      assertNotNull("no PSI element at offset " + offset, element);
      return new CodeownersOwnerGotoDeclarationHandler()
              .getGotoDeclarationTargets(element, offset, fixture.getEditor());
    });
  }

  @Test
  public void getGotoDeclarationTargets_userOwner_returnsBrowserTargetToProfile() {
    // Arrange
    remoteService().setRemoteUrlForTesting("git@github.com:org/repo.git");

    // Act
    PsiElement[] targets = targetsAt("*.java @alice\n", "@alice");

    // Assert
    assertNotNull(targets);
    assertEquals(1, targets.length);
    assertTrue("expected a browser target, got: " + targets[0], targets[0] instanceof OpenUrlInBrowserElement);
    OpenUrlInBrowserElement target = (OpenUrlInBrowserElement) targets[0];
    assertEquals("https://github.com/alice", target.getName());
    // The target opens externally rather than moving the caret in-editor.
    assertTrue(target.canNavigate());
    assertFalse(target.canNavigateToSource());
    assertNull(target.getContainingFile());
    assertSame(fixture.getProject(), target.getProject());
  }

  @Test
  public void getGotoDeclarationTargets_teamOwner_returnsBrowserTargetToTeamPage() {
    // Arrange
    remoteService().setRemoteUrlForTesting("git@github.com:acme/repo.git");

    // Act
    PsiElement[] targets = targetsAt("*.java @acme/backend\n", "@acme/backend");

    // Assert
    assertNotNull(targets);
    assertEquals(1, targets.length);
    assertEquals("https://github.com/orgs/acme/teams/backend",
            ((OpenUrlInBrowserElement) targets[0]).getName());
  }

  @Test
  public void getGotoDeclarationTargets_roleOwner_returnsNull() {
    // Arrange — GitLab roles are not pages; the handler ignores the token.
    remoteService().setRemoteUrlForTesting("git@gitlab.com:group/repo.git");

    // Act
    PsiElement[] targets = targetsAt("*.java @@maintainer\n", "@@maintainer");

    // Assert
    assertNull(targets);
  }

  @Test
  public void getGotoDeclarationTargets_emailOwner_returnsNull() {
    // Arrange
    remoteService().setRemoteUrlForTesting("git@github.com:org/repo.git");

    // Act
    PsiElement[] targets = targetsAt("*.java alice@example.com\n", "alice@example.com");

    // Assert
    assertNull(targets);
  }

  @Test
  public void getGotoDeclarationTargets_patternToken_returnsNull() {
    // Arrange — Ctrl+Click on the glob pattern, not an owner.
    remoteService().setRemoteUrlForTesting("git@github.com:org/repo.git");

    // Act — "*.java" starts at offset 0.
    PsiElement[] targets = targetsAt("*.java @alice\n", "*.java");

    // Assert
    assertNull(targets);
  }

  @Test
  public void getGotoDeclarationTargets_noGitRemote_returnsNull() {
    // Arrange — service left unseeded in setUp(), so there is no remote.

    // Act
    PsiElement[] targets = targetsAt("*.java @alice\n", "@alice");

    // Assert — a valid owner token, but nothing to link to.
    assertNull(targets);
  }
}
