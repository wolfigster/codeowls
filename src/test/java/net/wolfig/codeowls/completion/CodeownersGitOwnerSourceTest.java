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
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CodeownersGitOwnerSource} — exercises the source via the
 * injectable contributor supplier so no real Git binary or repository is
 * required.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersGitOwnerSourceTest {

  private CodeInsightTestFixture fixture;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
    // Reset before each test so the previous test's cache doesn't bleed in.
    CodeownersGitContributorService.getInstance(fixture.getProject())
            .setCachedContributorsForTesting(List.of());
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      // Hand the shared light project back with an empty cache so the next
      // class doesn't inherit our test contributors.
      CodeownersGitContributorService.getInstance(fixture.getProject())
              .setCachedContributorsForTesting(List.of());
      fixture.tearDown();
      fixture = null;
    }
  }

  private PsiFile codeowners(String content) {
    return fixture.addFileToProject("CODEOWNERS", content);
  }

  private List<String> ownerStrings(List<CodeownersOwnerCollector.OwnerCandidate> candidates) {
    return candidates.stream().map(CodeownersOwnerCollector.OwnerCandidate::owner).collect(Collectors.toList());
  }

  @Test
  public void collect_supplierReturnsContributors_yieldsOwnerCandidatesTaggedWithGitSource() {
    // Arrange
    PsiFile file = codeowners("");
    CodeownersGitOwnerSource source = new CodeownersGitOwnerSource(
            project -> List.of("alice@example.com", "bob@example.com"));

    // Act
    List<CodeownersOwnerCollector.OwnerCandidate> candidates =
            ReadAction.compute(() -> source.collect(file));

    // Assert
    assertEquals(List.of("alice@example.com", "bob@example.com"), ownerStrings(candidates));
    for (CodeownersOwnerCollector.OwnerCandidate c : candidates) {
      assertEquals(CodeownersOwnerCollector.SOURCE_GIT_HISTORY, c.source());
    }
  }

  @Test
  public void collect_supplierReturnsEmpty_yieldsNoCandidates() {
    // Arrange
    PsiFile file = codeowners("");
    CodeownersGitOwnerSource source = new CodeownersGitOwnerSource(project -> List.of());

    // Act
    List<CodeownersOwnerCollector.OwnerCandidate> candidates =
            ReadAction.compute(() -> source.collect(file));

    // Assert
    assertTrue(candidates.isEmpty());
  }

  @Test
  public void collect_viaServiceTestSeam_returnsServiceCache() {
    // Arrange — populate the project service's cache via its test seam so
    // the default supplier (which reads from the service) sees those entries.
    PsiFile file = codeowners("");
    CodeownersGitContributorService.getInstance(fixture.getProject())
            .setCachedContributorsForTesting(List.of("carol@example.com"));
    CodeownersGitOwnerSource source = new CodeownersGitOwnerSource();

    // Act
    List<CodeownersOwnerCollector.OwnerCandidate> candidates =
            ReadAction.compute(() -> source.collect(file));

    // Assert
    assertEquals(List.of("carol@example.com"), ownerStrings(candidates));
    assertEquals(CodeownersOwnerCollector.SOURCE_GIT_HISTORY, candidates.getFirst().source());
  }

  @Test
  public void collector_defaultWithCurrentFileAndGit_dedupsAndKeepsFirstSourceTag() {
    // Arrange — owner @alice appears both in the CODEOWNERS file and in the
    // Git contributor cache. The current-file source comes first, so its tag
    // should win for that entry; the git-only contributor stays with the git
    // tag.
    PsiFile file = codeowners("*.java @alice\n");
    CodeownersGitContributorService.getInstance(fixture.getProject())
            .setCachedContributorsForTesting(List.of("@alice", "dan@example.com"));
    CodeownersOwnerCollector collector = new CodeownersOwnerCollector();

    // Act
    List<CodeownersOwnerCollector.OwnerCandidate> candidates =
            ReadAction.compute(() -> collector.collect(file));

    // Assert
    assertEquals(List.of("@alice", "dan@example.com", "@@developer", "@@maintainer", "@@owner"), ownerStrings(candidates));
    assertEquals(CodeownersOwnerCollector.SOURCE_CURRENT_FILE, candidates.get(0).source());
    assertEquals(CodeownersOwnerCollector.SOURCE_GIT_HISTORY, candidates.get(1).source());
  }
}
