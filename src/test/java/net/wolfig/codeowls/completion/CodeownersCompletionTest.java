package net.wolfig.codeowls.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.EdtTestUtil;
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
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * End-to-end completion tests driven through {@link CodeInsightTestFixture}.
 *
 * <p>The fixture parses the {@code <caret>} marker, runs basic completion,
 * and exposes either the auto-inserted result or the full list of lookup
 * strings. We use both: list-checks for "did the right suggestions appear"
 * and result-text checks for "did selecting the unique match insert
 * correctly".
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersCompletionTest {

  private CodeInsightTestFixture fixture;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
    // Reset the shared Git contributor cache between tests — tests that need
    // contributors call setCachedContributorsForTesting explicitly.
    CodeownersGitContributorService.getInstance(fixture.getProject())
            .setCachedContributorsForTesting(java.util.List.of());
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  private List<String> completeAndCollect(String codeownersContent) {
    fixture.configureByText(net.wolfig.codeowls.lang.CodeownersFileType.INSTANCE, codeownersContent);
    LookupElement[] elements = fixture.complete(CompletionType.BASIC);
    if (elements == null) return List.of();
    return Stream.of(elements)
            .map(LookupElement::getLookupString)
            .collect(Collectors.toList());
  }

  // -- pattern segment ------------------------------------------------------

  @Test
  public void pathCompletion_emptyLine_suggestsTopLevelEntries() {
    // Arrange
    fixture.addFileToProject("src/Foo.java", "");
    fixture.addFileToProject("docs/README.md", "");
    fixture.addFileToProject(".gitignore", "");

    // Act
    List<String> suggestions = completeAndCollect("<caret>");

    // Assert
    assertTrue("expected src/ in " + suggestions, suggestions.contains("src/"));
    assertTrue("expected docs/ in " + suggestions, suggestions.contains("docs/"));
    assertTrue("expected .gitignore in " + suggestions, suggestions.contains(".gitignore"));
  }

  @Test
  public void pathCompletion_partialTopLevelEntry_filtersByPrefix() {
    // Arrange — two entries start with "d" so the lookup popup stays open and
    // we can verify what's in (and out of) it.
    fixture.addFileToProject("src/Foo.java", "");
    fixture.addFileToProject("docs/README.md", "");
    fixture.addFileToProject("data/foo.txt", "");

    // Act
    List<String> suggestions = completeAndCollect("d<caret>");

    // Assert
    assertTrue("expected docs/ in " + suggestions, suggestions.contains("docs/"));
    assertTrue("expected data/ in " + suggestions, suggestions.contains("data/"));
    assertFalse("expected src/ filtered out: " + suggestions, suggestions.contains("src/"));
  }

  @Test
  public void pathCompletion_insideSubdirectory_suggestsDirectChildren() {
    // Arrange
    fixture.addFileToProject("src/main/Foo.java", "");
    fixture.addFileToProject("src/test/Bar.java", "");
    fixture.addFileToProject("docs/README.md", "");

    // Act — caret after "src/" only lists children of src/.
    List<String> suggestions = completeAndCollect("src/<caret>");

    // Assert
    assertTrue(suggestions.contains("main/"));
    assertTrue(suggestions.contains("test/"));
    assertFalse(suggestions.contains("docs/"));
  }

  @Test
  public void pathCompletion_partialChildPrefix_uniqueMatchIsInserted() {
    // Arrange — only one match, so the platform inserts it directly.
    fixture.addFileToProject("src/main/Foo.java", "");
    fixture.configureByText(net.wolfig.codeowls.lang.CodeownersFileType.INSTANCE, "src/ma<caret>");

    // Act
    fixture.complete(CompletionType.BASIC);

    // Assert
    fixture.checkResult("src/main/<caret>");
  }

  @Test
  public void pathCompletion_ignoredDirectories_areNotSuggested() {
    // Arrange — both .git and node_modules live under the project root.
    fixture.addFileToProject(".git/config", "");
    fixture.addFileToProject("node_modules/foo/index.js", "");
    fixture.addFileToProject("src/Foo.java", "");

    // Act
    List<String> suggestions = completeAndCollect("<caret>");

    // Assert
    assertTrue(suggestions.contains("src/"));
    assertFalse("expected .git/ to be filtered: " + suggestions, suggestions.contains(".git/"));
    assertFalse("expected node_modules/ to be filtered: " + suggestions, suggestions.contains("node_modules/"));
  }

  @Test
  public void pathCompletion_insideCommentLine_doesNothing() {
    // Arrange
    fixture.addFileToProject("src/Foo.java", "");

    // Act
    List<String> suggestions = completeAndCollect("# top-level <caret>");

    // Assert — no path nor owner candidates in comments.
    assertFalse(suggestions.contains("src/"));
  }

  // -- owner segment --------------------------------------------------------

  @Test
  public void ownerCompletion_afterPattern_suggestsOwnersUsedElsewhereInFile() {
    // Arrange
    String content =
            """
                    *.md @docs-team
                    *.java @backend
                    /src/Foo.java <caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue(suggestions.contains("@docs-team"));
    assertTrue(suggestions.contains("@backend"));
  }

  @Test
  public void ownerCompletion_partialOwnerPrefix_filtersByTypedToken() {
    // Arrange — two owners start with "@ba" so the popup stays open.
    String content =
            """
                    *.md @docs-team
                    *.java @backend @backup-owner @alice
                    /src/Foo.java @ba<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected @backend in " + suggestions, suggestions.contains("@backend"));
    assertTrue("expected @backup-owner in " + suggestions, suggestions.contains("@backup-owner"));
    assertFalse("expected @alice filtered out for prefix '@ba': " + suggestions,
            suggestions.contains("@alice"));
  }

  @Test
  public void ownerCompletion_secondOwnerOnSameLine_uniqueMatchIsInserted() {
    // Arrange — only one owner in the file starts with "@fr"; the platform
    // auto-inserts it without showing the popup, so verify via result text.
    String content =
            """
                    *.md @docs-team
                    *.java @backend @frontend
                    /src/Foo.java @docs-team @fr<caret>
                    """;
    fixture.configureByText(net.wolfig.codeowls.lang.CodeownersFileType.INSTANCE, content);

    // Act
    fixture.complete(CompletionType.BASIC);

    // Assert
    fixture.checkResult(
            """
                    *.md @docs-team
                    *.java @backend @frontend
                    /src/Foo.java @docs-team @frontend<caret>
                    """);
  }

  @Test
  public void ownerCompletion_inEmptyCodeownersFile_suggestsBuiltinRoles() {
    // Arrange — no other sources contribute (no other owners in the file, Git
    // cache cleared in setUp), so only the built-in GitLab roles remain.
    String content = "/src/Foo.java <caret>";

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected @@developer in " + suggestions, suggestions.contains("@@developer"));
    assertTrue("expected @@maintainer in " + suggestions, suggestions.contains("@@maintainer"));
    assertTrue("expected @@owner in " + suggestions, suggestions.contains("@@owner"));
  }

  @Test
  public void ownerCompletion_singleAtPrefix_includesTeamStyleOwners() {
    // Arrange — team owners contain a slash, which IntelliJ's default
    // CamelHumpMatcher treats as a hard separator. Typing just '@' must still
    // surface them, so the provider uses a plain startsWith matcher.
    String content =
            """
                    *.md @team/frontend
                    *.java @backend
                    /src/Foo.java @<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected @team/frontend in " + suggestions,
            suggestions.contains("@team/frontend"));
    assertTrue("expected @backend in " + suggestions, suggestions.contains("@backend"));
  }

  @Test
  public void ownerCompletion_teamPrefix_filtersTeamCandidates() {
    // Arrange — typing through a team prefix that includes the slash still
    // matches the expected candidate, so completion stays useful while drilling
    // into an org's team namespace.
    String content =
            """
                    *.md @team/frontend
                    *.java @team/backend
                    *.ts @docs
                    /src/Foo.java @team/f<caret>
                    """;
    fixture.configureByText(net.wolfig.codeowls.lang.CodeownersFileType.INSTANCE, content);

    // Act
    fixture.complete(CompletionType.BASIC);

    // Assert — only @team/frontend matches the prefix, so it's auto-inserted.
    fixture.checkResult(
            """
                    *.md @team/frontend
                    *.java @team/backend
                    *.ts @docs
                    /src/Foo.java @team/frontend<caret>
                    """);
  }

  @Test
  public void ownerCompletion_doubleAtPrefix_filtersToGitlabRoles() {
    // Arrange — once the user types "@@", only the role candidates match.
    String content =
            """
                    *.md @docs-team
                    /src/Foo.java @@<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected @@developer in " + suggestions, suggestions.contains("@@developer"));
    assertTrue("expected @@maintainer in " + suggestions, suggestions.contains("@@maintainer"));
    assertTrue("expected @@owner in " + suggestions, suggestions.contains("@@owner"));
    assertFalse("@docs-team should not match prefix '@@': " + suggestions,
            suggestions.contains("@docs-team"));
  }

  // -- mode separation ------------------------------------------------------

  @Test
  public void completion_inPatternSegment_doesNotSuggestOwners() {
    // Arrange — file already has owners, but the caret is in the pattern.
    fixture.addFileToProject("src/Foo.java", "");
    String content =
            """
                    *.java @backend
                    /src/<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertFalse("@backend should not show up while typing a path: " + suggestions,
            suggestions.contains("@backend"));
  }

  @Test
  public void completion_inOwnerSegment_doesNotSuggestPaths() {
    // Arrange — two owners exist so the popup stays open and we can assert
    // both that owners ARE suggested and that paths are NOT.
    fixture.addFileToProject("src/Foo.java", "");
    String content =
            """
                    *.java @backend
                    *.md @docs
                    /src/Foo.java @<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue(suggestions.contains("@backend"));
    assertTrue(suggestions.contains("@docs"));
    assertFalse("src/ should not show up as an owner: " + suggestions,
            suggestions.contains("src/"));
  }

  @Test
  public void completion_inSectionHeaderName_suggestsExistingSectionNamesNotPathsOrOwners() {
    // Arrange — multiple "B*" section names keep the popup open; the file also
    // contains a project path and an owner, neither of which should appear.
    fixture.addFileToProject("src/Foo.java", "");
    String content =
            """
                    [Backend]
                    *.java @backend
                    [Backups]
                    *.tar @ops
                    [Frontend]
                    *.ts @frontend
                    [B<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected Backend in " + suggestions, suggestions.contains("Backend"));
    assertTrue("expected Backups in " + suggestions, suggestions.contains("Backups"));
    assertFalse("Frontend should be filtered by prefix 'B': " + suggestions,
            suggestions.contains("Frontend"));
    assertFalse("no path candidates expected in section header name: " + suggestions,
            suggestions.contains("src/"));
    assertFalse("no owner candidates expected in section header name: " + suggestions,
            suggestions.contains("@backend"));
  }

  @Test
  public void completion_inSectionHeaderName_uniqueMatchIsInserted() {
    // Arrange — only one section name matches the typed prefix, so the
    // platform should auto-insert it.
    String content =
            """
                    [Backend]
                    *.java @backend
                    [Frontend]
                    *.ts @frontend
                    [Fr<caret>
                    """;
    fixture.configureByText(net.wolfig.codeowls.lang.CodeownersFileType.INSTANCE, content);

    // Act
    fixture.complete(CompletionType.BASIC);

    // Assert
    fixture.checkResult(
            """
                    [Backend]
                    *.java @backend
                    [Frontend]
                    *.ts @frontend
                    [Frontend<caret>
                    """);
  }

  @Test
  public void completion_inSectionHeaderNameWithEmptyPrefix_suggestsAllSectionNames() {
    // Arrange — caret right after '[' with no characters typed yet.
    String content =
            """
                    [Backend]
                    *.java @backend
                    [Frontend]
                    *.ts @frontend
                    [<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected Backend in " + suggestions, suggestions.contains("Backend"));
    assertTrue("expected Frontend in " + suggestions, suggestions.contains("Frontend"));
  }

  @Test
  public void ownerCompletion_includesGitContributorsAlongsideCurrentFileOwners() {
    // Arrange — current file has one owner, Git cache has another; both
    // should surface in the owner suggestion list.
    CodeownersGitContributorService.getInstance(fixture.getProject())
            .setCachedContributorsForTesting(List.of("alice@example.com"));
    String content =
            """
                    *.md @docs
                    /src/Foo.java <caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected @docs in " + suggestions, suggestions.contains("@docs"));
    assertTrue("expected git contributor in " + suggestions, suggestions.contains("alice@example.com"));
  }

  @Test
  public void completion_inSectionHeaderDefaultOwners_suggestsOwners() {
    // Arrange — caret in the default-owner slot after [Section][N]. Owner
    // suggestions should come from the rest of the file.
    String content =
            """
                    *.java @backend
                    *.md @docs
                    [Documentation][2] @<caret>
                    """;

    // Act
    List<String> suggestions = completeAndCollect(content);

    // Assert
    assertTrue("expected @backend in " + suggestions, suggestions.contains("@backend"));
    assertTrue("expected @docs in " + suggestions, suggestions.contains("@docs"));
  }

  @Test
  public void completion_inCommentLine_doesNothing() {
    // Arrange
    fixture.addFileToProject("src/Foo.java", "");
    String content = "# Section heading <caret>\n";

    // Act
    fixture.configureByText(net.wolfig.codeowls.lang.CodeownersFileType.INSTANCE, content);
    LookupElement[] elements = fixture.complete(CompletionType.BASIC);

    // Assert — defensive: comments must not surface any of our candidates.
    if (elements != null) {
      assertFalse(Arrays.stream(elements).map(LookupElement::getLookupString)
              .anyMatch(s -> s.equals("src/") || s.startsWith("@")));
    }
  }

  // -- typed-handler wiring -------------------------------------------------

  @Test
  public void typedHandler_atSymbol_stopsAndSchedulesAutoPopup_otherCharsContinue() {
    // Arrange — auto-popup on '@' moved out of the contributor onto a dedicated
    // TypedHandlerDelegate. Drive checkAutoPopup directly so the trigger char
    // set stays locked in.
    fixture.configureByText(net.wolfig.codeowls.lang.CodeownersFileType.INSTANCE,
            "/src/Foo.java <caret>");
    CodeownersTypedHandler handler = new CodeownersTypedHandler();

    // Act — AutoPopupController.scheduleAutoPopup asserts EDT, so the call
    // must run on the platform's event-dispatch thread.
    TypedHandlerDelegate.Result atResult = EdtTestUtil.runInEdtAndGet(() ->
            handler.checkAutoPopup('@', fixture.getProject(), fixture.getEditor(), fixture.getFile()));
    TypedHandlerDelegate.Result spaceResult = EdtTestUtil.runInEdtAndGet(() ->
            handler.checkAutoPopup(' ', fixture.getProject(), fixture.getEditor(), fixture.getFile()));

    // Assert — '@' is handled (popup scheduled, no further delegates run);
    // anything else passes through untouched.
    assertEquals(TypedHandlerDelegate.Result.STOP, atResult);
    assertEquals(TypedHandlerDelegate.Result.CONTINUE, spaceResult);
  }
}
