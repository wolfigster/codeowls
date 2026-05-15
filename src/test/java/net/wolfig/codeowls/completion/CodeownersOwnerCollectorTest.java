package net.wolfig.codeowls.completion;

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
 * Tests for {@link CodeownersOwnerCollector} — covers the
 * {@link CodeownersOwnerCollector.CurrentFileSource} and the dedup logic
 * shared by all sources.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersOwnerCollectorTest {

  private CodeInsightTestFixture fixture;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
    // Light fixtures reuse the same project across tests in the JVM, so the
    // Git contributor cache can leak from another test. Reset it to a known
    // empty state before each test that doesn't explicitly populate it.
    CodeownersGitContributorService.getInstance(fixture.getProject())
            .setCachedContributorsForTesting(List.of());
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  private PsiFile addCodeownersFile(String content) {
    return fixture.addFileToProject("CODEOWNERS", content);
  }

  private List<String> ownerStrings(List<CodeownersOwnerCollector.OwnerCandidate> candidates) {
    return candidates.stream().map(CodeownersOwnerCollector.OwnerCandidate::owner).collect(Collectors.toList());
  }

  @Test
  public void collect_currentFileWithSeveralRules_returnsEveryOwnerOncePerSource() {
    // Arrange
    PsiFile codeowners = addCodeownersFile(
            """
                    *.java @backend @alice
                    docs/ @docs-team
                    *.md @alice
                    """);
    CodeownersOwnerCollector collector = new CodeownersOwnerCollector();

    // Act
    List<CodeownersOwnerCollector.OwnerCandidate> candidates =
            com.intellij.openapi.application.ReadAction.compute(() -> collector.collect(codeowners));

    // Assert
    assertEquals(List.of("@backend", "@alice", "@docs-team"), ownerStrings(candidates));
    for (CodeownersOwnerCollector.OwnerCandidate c : candidates) {
      assertEquals(CodeownersOwnerCollector.SOURCE_CURRENT_FILE, c.source());
    }
  }

  @Test
  public void collect_emptyFile_returnsNoCandidates() {
    // Arrange
    PsiFile codeowners = addCodeownersFile("");
    CodeownersOwnerCollector collector = new CodeownersOwnerCollector();

    // Act
    List<CodeownersOwnerCollector.OwnerCandidate> candidates =
            com.intellij.openapi.application.ReadAction.compute(() -> collector.collect(codeowners));

    // Assert
    assertTrue(candidates.isEmpty());
  }

  @Test
  public void collect_multipleSources_firstSourceWinsPerOwner() {
    // Arrange — a CurrentFile source plus a synthetic "from Git history" source
    // that lists @alice as well. The current-file source comes first and should
    // keep ownership of @alice in the dedup'd output.
    PsiFile codeowners = addCodeownersFile("*.java @alice\n");
    CodeownersOwnerCollector.Source githubStub = file -> List.of(
            new CodeownersOwnerCollector.OwnerCandidate("@alice", CodeownersOwnerCollector.SOURCE_GIT_HISTORY),
            new CodeownersOwnerCollector.OwnerCandidate("alice@example.com", CodeownersOwnerCollector.SOURCE_GIT_HISTORY));
    CodeownersOwnerCollector collector = new CodeownersOwnerCollector(
            List.of(new CodeownersOwnerCollector.CurrentFileSource(), githubStub));

    // Act
    List<CodeownersOwnerCollector.OwnerCandidate> candidates =
            com.intellij.openapi.application.ReadAction.compute(() -> collector.collect(codeowners));

    // Assert
    assertEquals(List.of("@alice", "alice@example.com"), ownerStrings(candidates));
    assertEquals(CodeownersOwnerCollector.SOURCE_CURRENT_FILE, candidates.get(0).source());
    assertEquals(CodeownersOwnerCollector.SOURCE_GIT_HISTORY, candidates.get(1).source());
  }
}
