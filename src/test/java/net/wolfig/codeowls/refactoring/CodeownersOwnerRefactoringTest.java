package net.wolfig.codeowls.refactoring;

import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.OwnerToken;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.Plan;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.SectionRange;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for the "Refactor Owner" model: which token the caret is on, which
 * GitLab section encloses it, which occurrences a scope covers, and the exact
 * text the refactoring produces.
 *
 * <p>All of it is pure string logic driven by the shared lexer, so these tests
 * run standalone — no fixture. {@link CodeownersOwnerRefactoringCommandTest}
 * covers applying the result to a real document.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersOwnerRefactoringTest {

  /**
   * Offset of the {@code n}-th (0-based) occurrence of {@code needle}.
   */
  private static int offsetOf(String content, String needle, int n) {
    int at = -1;
    for (int i = 0; i <= n; i++) {
      at = content.indexOf(needle, at + 1);
      assertTrue("no occurrence #" + n + " of '" + needle + "'", at >= 0);
    }
    return at;
  }

  private static int offsetOf(String content, String needle) {
    return offsetOf(content, needle, 0);
  }

  /**
   * Runs the whole refactoring the way the action does — resolve the owner at
   * {@code offset}, resolve its section, plan, apply — and returns the new text.
   */
  private static String refactor(String content, int offset, String newOwner, OwnerRefactoringScope scope) {
    Plan plan = planAt(content, offset, newOwner, scope);
    return CodeownersOwnerRefactoring.applyEdits(content, plan.edits());
  }

  private static Plan planAt(String content, int offset, String newOwner, OwnerRefactoringScope scope) {
    OwnerToken owner = CodeownersOwnerRefactoring.ownerTokenAt(content, offset);
    assertNotNull("no owner token at offset " + offset, owner);
    SectionRange section = CodeownersOwnerRefactoring.sectionAt(content, owner.startOffset());
    return CodeownersOwnerRefactoring.plan(content, owner, section, scope, newOwner);
  }

  // -- owner identification -------------------------------------------------

  @Test
  public void ownerTokenAt_ownerToken_isFound() {
    // Arrange
    String content = "/api/** @alice\n";

    // Act
    OwnerToken owner = CodeownersOwnerRefactoring.ownerTokenAt(content, offsetOf(content, "@alice") + 2);

    // Assert
    assertNotNull(owner);
    assertEquals("@alice", owner.text());
    assertEquals(offsetOf(content, "@alice"), owner.startOffset());
  }

  @Test
  public void ownerTokenAt_caretRightAfterOwner_isFound() {
    // Arrange — a caret parked at the end of the token still names it.
    String content = "/api/** @alice\n";

    // Act
    OwnerToken owner = CodeownersOwnerRefactoring.ownerTokenAt(content, content.indexOf("\n"));

    // Assert
    assertNotNull(owner);
    assertEquals("@alice", owner.text());
  }

  @Test
  public void ownerTokenAt_nonOwnerPositions_areRejected() {
    // Arrange — pattern, comment (even one naming an owner), section name,
    // approval count and whitespace must never resolve to an owner.
    String content = "# ask @alice first\n[Backend][2] @bob\n/api/** @carol\n";

    // Act / Assert
    assertNull(CodeownersOwnerRefactoring.ownerTokenAt(content, offsetOf(content, "@alice")));
    assertNull(CodeownersOwnerRefactoring.ownerTokenAt(content, offsetOf(content, "Backend")));
    assertNull(CodeownersOwnerRefactoring.ownerTokenAt(content, offsetOf(content, "[2]") + 1));
    assertNull(CodeownersOwnerRefactoring.ownerTokenAt(content, offsetOf(content, "/api/**") + 2));
  }

  @Test
  public void ownerTokens_everyOwnerType_isRecognised() {
    // Arrange
    String content = "/a @alice @org/team @@maintainer alice@example.com\n";

    // Act
    var tokens = CodeownersOwnerRefactoring.ownerTokens(content);

    // Assert
    assertEquals(4, tokens.size());
    assertEquals("@alice", tokens.get(0).text());
    assertEquals("@org/team", tokens.get(1).text());
    assertEquals("@@maintainer", tokens.get(2).text());
    assertEquals("alice@example.com", tokens.get(3).text());
  }

  // -- section detection ----------------------------------------------------

  @Test
  public void sectionAt_ownerInSection_resolvesToEnclosingSection() {
    // Arrange
    String content = """
            [Backend]
            /backend/** @alice
            
            [Frontend]
            /frontend/** @alice
            """;

    // Act
    SectionRange first = CodeownersOwnerRefactoring.sectionAt(content, offsetOf(content, "@alice", 0));
    SectionRange second = CodeownersOwnerRefactoring.sectionAt(content, offsetOf(content, "@alice", 1));

    // Assert
    assertNotNull(first);
    assertEquals("Backend", first.name());
    assertNotNull(second);
    assertEquals("Frontend", second.name());
  }

  @Test
  public void sectionAt_ownerBeforeAnySection_hasNoSection() {
    // Arrange
    String content = "/api/** @alice\n\n[Backend]\n/backend/** @alice\n";

    // Act
    SectionRange section = CodeownersOwnerRefactoring.sectionAt(content, offsetOf(content, "@alice", 0));

    // Assert
    assertNull(section);
  }

  @Test
  public void sectionAt_ownerOnSectionHeader_belongsToThatSection() {
    // Arrange — GitLab default owners live on the header line itself.
    String content = "[Backend][2] @alice @backend-team\n/api/**\n";

    // Act
    SectionRange section = CodeownersOwnerRefactoring.sectionAt(content, offsetOf(content, "@alice"));

    // Assert
    assertNotNull(section);
    assertEquals("Backend", section.name());
    assertEquals(0, section.startOffset());
    assertEquals(content.length(), section.endOffset());
  }

  @Test
  public void sectionAt_optionalSection_isRecognised() {
    // Arrange
    String content = "^[Optional]\n/api/** @alice\n";

    // Act
    SectionRange section = CodeownersOwnerRefactoring.sectionAt(content, offsetOf(content, "@alice"));

    // Assert
    assertNotNull(section);
    assertEquals("Optional", section.name());
  }

  @Test
  public void sections_sectionEndsAtNextHeader() {
    // Arrange
    String content = "[Backend]\n/api/** @alice\n[Frontend]\n/ui/** @bob\n";

    // Act
    SectionRange backend = CodeownersOwnerRefactoring.sections(content).getFirst();

    // Assert
    assertEquals(0, backend.startOffset());
    assertEquals(content.indexOf("[Frontend]"), backend.endOffset());
  }

  // -- file scope -----------------------------------------------------------

  @Test
  public void fileScope_replacesEveryOccurrence() {
    // Arrange
    String content = """
            /backend/** @alice
            
            [Frontend]
            /frontend/** @alice @frontend-team
            /frontend/components/** @alice
            
            [Documentation]
            /docs/** @alice
            """;

    // Act — invoked from inside [Frontend], but with file scope.
    String result = refactor(content, offsetOf(content, "@alice", 1), "@bob", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("""
            /backend/** @bob
            
            [Frontend]
            /frontend/** @bob @frontend-team
            /frontend/components/** @bob
            
            [Documentation]
            /docs/** @bob
            """, result);
  }

  @Test
  public void fileScope_ownerOutsideAnySection_isReplaced() {
    // Arrange — no sections at all; only file scope makes sense here.
    String content = "/api/** @alice\n/docs/** @alice\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** @bob\n/docs/** @bob\n", result);
  }

  // -- section scope --------------------------------------------------------

  @Test
  public void sectionScope_replacesOnlyInsideCurrentSection() {
    // Arrange
    String content = """
            /backend/** @alice
            
            [Frontend]
            /frontend/** @alice @frontend-team
            /frontend/components/** @alice
            
            [Documentation]
            /docs/** @alice
            """;

    // Act
    String result = refactor(content, offsetOf(content, "@alice", 1), "@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("""
            /backend/** @alice
            
            [Frontend]
            /frontend/** @bob @frontend-team
            /frontend/components/** @bob
            
            [Documentation]
            /docs/** @alice
            """, result);
  }

  @Test
  public void sectionScope_sameOwnerInMultipleSections_touchesOnlySelectedSection() {
    // Arrange
    String content = """
            [Backend]
            /api/** @alice
            
            [Frontend]
            /ui/** @alice
            """;

    // Act — the occurrence in [Backend].
    String result = refactor(content, offsetOf(content, "@alice", 0), "@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("""
            [Backend]
            /api/** @bob
            
            [Frontend]
            /ui/** @alice
            """, result);
  }

  @Test
  public void sectionScope_multipleOccurrencesInSection_areAllReplaced() {
    // Arrange
    String content = """
            [Backend]
            /api/** @alice
            /database/** @alice
            /jobs/** @alice
            
            [Frontend]
            /ui/** @alice
            """;

    // Act
    Plan plan = planAt(content, offsetOf(content, "@alice", 1), "@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals(3, plan.occurrenceCount());
    assertEquals("""
            [Backend]
            /api/** @bob
            /database/** @bob
            /jobs/** @bob
            
            [Frontend]
            /ui/** @alice
            """, CodeownersOwnerRefactoring.applyEdits(content, plan.edits()));
  }

  @Test
  public void sectionScope_sectionDefaultOwnerOnHeader_isReplaced() {
    // Arrange — the header is part of the section range, so its default owners
    // are covered too.
    String content = """
            [Backend] @alice
            /api/**
            /database/**
            
            [Frontend]
            /ui/** @alice
            """;

    // Act
    String result = refactor(content, offsetOf(content, "@alice", 0), "@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("""
            [Backend] @bob
            /api/**
            /database/**
            
            [Frontend]
            /ui/** @alice
            """, result);
  }

  @Test
  public void sectionScope_selectedOnRule_alsoReplacesHeaderDefaultOwner() {
    // Arrange
    String content = "[Backend][2] @alice\n/api/** @alice\n";

    // Act — selected on the rule, not on the header.
    String result = refactor(content, offsetOf(content, "@alice", 1), "@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("[Backend][2] @bob\n/api/** @bob\n", result);
  }

  // -- precision ------------------------------------------------------------

  @Test
  public void similarOwners_areLeftUntouched() {
    // Arrange
    String content = "/a @alice\n/b @alice-team\n/c @alice2\n/d alice@example.com\n/e @Alice\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/a @bob\n/b @alice-team\n/c @alice2\n/d alice@example.com\n/e @Alice\n", result);
  }

  @Test
  public void commentsMentioningTheOwner_areLeftUntouched() {
    // Arrange
    String content = """
            # Ask @alice before modifying this
            /api/** @alice
            # @alice also reviews the UI
            /ui/** @bob
            """;

    // Act
    String result = refactor(content, offsetOf(content, "@alice", 1), "@carol", OwnerRefactoringScope.FILE);

    // Assert — only the real owner token changed; both comments are intact.
    assertEquals("""
            # Ask @alice before modifying this
            /api/** @carol
            # @alice also reviews the UI
            /ui/** @bob
            """, result);
  }

  @Test
  public void ownerAfterAnInlineHash_followsTheLexer() {
    // Arrange — the CODEOWNERS lexer only knows whole-line comments: on a rule
    // line "#" is just a bad character, and a following "@name" is still lexed
    // (and syntax-highlighted, and attributed to the rule) as an owner. The
    // refactoring deliberately agrees with the parser rather than inventing an
    // inline-comment rule of its own; whole-line comments are unaffected.
    String content = "/api/** @bob # ask @alice\n/ui/** @alice\n";

    // Act — the unambiguous owner on the second line.
    String result = refactor(content, offsetOf(content, "@alice", 1), "@carol", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** @bob # ask @carol\n/ui/** @carol\n", result);
  }

  @Test
  public void patternResemblingTheOwner_isLeftUntouched() {
    // Arrange — the pattern token is not an owner, however it is spelled.
    String content = "@alice @alice\n";

    // Act — the owner (the second token; the first is the rule's pattern).
    String result = refactor(content, offsetOf(content, "@alice", 1), "@bob", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("@alice @bob\n", result);
  }

  // -- owner types ----------------------------------------------------------

  @Test
  public void emailOwner_isReplacedLikeAnyOther() {
    // Arrange
    String content = "/api/** alice@example.com\n/ui/** alice@example.com @bob\n";

    // Act
    String result = refactor(content, offsetOf(content, "alice@example.com"),
            "carol@example.com", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** carol@example.com\n/ui/** carol@example.com @bob\n", result);
  }

  @Test
  public void roleOwner_isReplacedLikeAnyOther() {
    // Arrange
    String content = "[Backend]\n/api/** @@developer\n/db/** @@developer\n";

    // Act
    String result = refactor(content, offsetOf(content, "@@developer"), "@@maintainer", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("[Backend]\n/api/** @@maintainer\n/db/** @@maintainer\n", result);
  }

  @Test
  public void teamOwner_isReplacedLikeAnyOther() {
    // Arrange
    String content = "/api/** @org/backend\n";

    // Act
    String result = refactor(content, offsetOf(content, "@org/backend"), "@org/platform", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** @org/platform\n", result);
  }

  // -- formatting -----------------------------------------------------------

  @Test
  public void formatting_isPreserved() {
    // Arrange — aligned columns, a tab, a trailing comment and no final newline.
    String content = """
            /api/**        @alice     @backend
            /ui/**\t@alice\t# owners here
            /last/** @alice""";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.FILE);

    // Assert — only the owner tokens themselves differ; no re-alignment.
    assertEquals("""
            /api/**        @bob     @backend
            /ui/**\t@bob\t# owners here
            /last/** @bob""", result);
  }

  @Test
  public void formatting_crlfLineEndings_arePreserved() {
    // Arrange
    String content = "[Backend]\r\n/api/** @alice\r\n/db/** @alice\r\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("[Backend]\r\n/api/** @bob\r\n/db/** @bob\r\n", result);
  }

  // -- no-ops ---------------------------------------------------------------

  @Test
  public void replacingWithTheSameOwner_changesNothing() {
    // Arrange
    String content = "/api/** @alice\n/ui/** @alice\n";

    // Act
    Plan plan = planAt(content, offsetOf(content, "@alice"), "@alice", OwnerRefactoringScope.FILE);

    // Assert
    assertTrue(plan.isNoOp());
    assertTrue(plan.edits().isEmpty());
    assertEquals(content, CodeownersOwnerRefactoring.applyEdits(content, plan.edits()));
  }

  @Test
  public void ownerAbsentFromSection_yieldsNoOccurrences() {
    // Arrange
    String content = "[Backend]\n/api/** @alice\n\n[Frontend]\n/ui/** @bob\n";

    // Act — @alice, but limited to a section it does not appear in.
    Plan plan = CodeownersOwnerRefactoring.plan(content, "@alice", "@carol",
            content.indexOf("[Frontend]"), content.length());

    // Assert
    assertEquals(0, plan.occurrenceCount());
    assertTrue(plan.isNoOp());
  }

  // -- duplicate handling ---------------------------------------------------

  @Test
  public void duplicateOwnerOnSameRule_isCollapsed() {
    // Arrange — @bob is already an owner of the rule being rewritten.
    String content = "/api/** @alice @bob\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.FILE);

    // Assert — one @bob, not two; the whitespace of the dropped owner goes too.
    assertEquals("/api/** @bob\n", result);
  }

  @Test
  public void duplicateOwnerBeforeTheReplacement_isCollapsed() {
    // Arrange — the pre-existing @bob comes first this time.
    String content = "/api/** @bob @alice @carol\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.FILE);

    // Assert — the first position is kept, the redundant one removed.
    assertEquals("/api/** @bob @carol\n", result);
  }

  @Test
  public void sameOwnerTwiceOnOneRule_collapsesToOne() {
    // Arrange — malformed but legal input.
    String content = "/api/** @alice @alice\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** @bob\n", result);
  }

  @Test
  public void preExistingDuplicateOnAnUntouchedLine_isLeftAlone() {
    // Arrange — the refactoring is not a general clean-up: duplicates it did
    // not create stay exactly where they are.
    String content = "/api/** @bob @bob\n/ui/** @alice\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@carol", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** @bob @bob\n/ui/** @carol\n", result);
  }

  @Test
  public void duplicateOnSectionHeader_isCollapsed() {
    // Arrange
    String content = "[Backend] @alice @bob\n/api/**\n";

    // Act
    String result = refactor(content, offsetOf(content, "@alice"), "@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("[Backend] @bob\n/api/**\n", result);
  }

  // -- validation -----------------------------------------------------------

  @Test
  public void isValidOwner_acceptsEverySupportedOwnerType() {
    assertTrue(CodeownersOwnerRefactoring.isValidOwner("@alice"));
    assertTrue(CodeownersOwnerRefactoring.isValidOwner("@org/team"));
    assertTrue(CodeownersOwnerRefactoring.isValidOwner("@org/sub/team"));
    assertTrue(CodeownersOwnerRefactoring.isValidOwner("@@developer"));
    assertTrue(CodeownersOwnerRefactoring.isValidOwner("@@maintainer"));
    assertTrue(CodeownersOwnerRefactoring.isValidOwner("@@owner"));
    assertTrue(CodeownersOwnerRefactoring.isValidOwner("alice@example.com"));
  }

  @Test
  public void isValidOwner_rejectsAnythingTheLexerWouldNotReadAsOneOwner() {
    assertFalse(CodeownersOwnerRefactoring.isValidOwner("hello world"));
    assertFalse(CodeownersOwnerRefactoring.isValidOwner("alice"));
    assertFalse(CodeownersOwnerRefactoring.isValidOwner(""));
    assertFalse(CodeownersOwnerRefactoring.isValidOwner(null));
    assertFalse(CodeownersOwnerRefactoring.isValidOwner("@alice @bob"));
    assertFalse(CodeownersOwnerRefactoring.isValidOwner("@alice\n"));
    assertFalse(CodeownersOwnerRefactoring.isValidOwner("# comment"));
  }
}
