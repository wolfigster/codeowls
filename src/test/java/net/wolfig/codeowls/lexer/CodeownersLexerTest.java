package net.wolfig.codeowls.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static net.wolfig.codeowls.lexer.CodeownersTokenTypes.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Behavioral tests for {@link CodeownersLexer}.
 *
 * <p>Each test drives the lexer over a small CODEOWNERS snippet and asserts the
 * resulting token stream. Coverage targets the three lexer states
 * ({@code LINE_START}, {@code OWNERS}, {@code AFTER_SECTION}) and every owner
 * classification path (user, team, role, email, bad character), plus edge cases
 * around newline handling ({@code \n}, {@code \r}, {@code \r\n}), unclosed
 * section brackets, and files without a trailing newline.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersLexerTest {

  private static Token tok(IElementType type, String text) {
    return new Token(type, text);
  }

  private static List<Token> lex(String input) {
    CodeownersLexer lexer = new CodeownersLexer();
    lexer.start(input, 0, input.length(), 0);
    List<Token> tokens = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      tokens.add(new Token(
              lexer.getTokenType(),
              input.substring(lexer.getTokenStart(), lexer.getTokenEnd())));
      lexer.advance();
    }
    return tokens;
  }

  /**
   * {@link #lex(String)} with all {@link TokenType#WHITE_SPACE} tokens dropped.
   */
  private static List<Token> significant(String input) {
    List<Token> all = lex(input);
    List<Token> filtered = new ArrayList<>(all.size());
    for (Token t : all) {
      if (t.type != TokenType.WHITE_SPACE) filtered.add(t);
    }
    return filtered;
  }

  @Test
  public void lex_emptyInput_producesNoTokens() {
    // Arrange
    String input = "";

    // Act
    List<Token> tokens = lex(input);

    // Assert
    assertEquals(List.of(), tokens);
  }

  // ---- empty / whitespace ----

  @Test
  public void lex_singleSpace_returnsOneWhitespaceToken() {
    // Arrange
    String input = " ";

    // Act
    List<Token> tokens = lex(input);

    // Assert
    assertEquals(1, tokens.size());
    assertEquals(TokenType.WHITE_SPACE, tokens.getFirst().type);
    assertEquals(" ", tokens.getFirst().text);
  }

  @Test
  public void lex_consecutiveHorizontalSpace_returnsOneToken() {
    // Arrange
    String input = "   \t  ";

    // Act
    List<Token> tokens = lex(input);

    // Assert
    assertEquals(1, tokens.size());
    assertEquals(input, tokens.getFirst().text);
  }

  @Test
  public void lex_multipleNewlines_emitsOneWhitespaceTokenEach() {
    // Arrange
    String input = " \n\t\n";

    // Act
    List<Token> tokens = lex(input);

    // Assert
    assertEquals(4, tokens.size());
    for (Token t : tokens) assertEquals(TokenType.WHITE_SPACE, t.type);
  }

  @Test
  public void lex_crlfNewline_absorbedAsSingleWhitespaceToken() {
    // Arrange
    String input = "# a\r\n# b\r\n";

    // Act
    List<Token> tokens = lex(input);

    // Assert
    assertEquals(4, tokens.size());
    assertEquals("\r\n", tokens.get(1).text);
    assertEquals("\r\n", tokens.get(3).text);
  }

  @Test
  public void lex_loneCarriageReturn_returnsOneWhitespaceToken() {
    // Arrange
    String input = "\r";

    // Act
    List<Token> tokens = lex(input);

    // Assert
    assertEquals(1, tokens.size());
    assertEquals("\r", tokens.getFirst().text);
    assertEquals(TokenType.WHITE_SPACE, tokens.getFirst().type);
  }

  // ---- comments ----

  @Test
  public void lex_commentLine_consumesToEndOfLine() {
    // Arrange
    String input = "# hello world";
    List<Token> expected = List.of(tok(COMMENT, "# hello world"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_commentFollowedByRule_endsCommentAtNewline() {
    // Arrange
    String input = "# hi\n* @alice";
    List<Token> expected = List.of(
            tok(COMMENT, "# hi"),
            tok(PATTERN, "*"),
            tok(USER_OWNER, "@alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_leadingWhitespaceBeforeComment_emitsWhitespaceThenComment() {
    // Arrange
    String input = "   # comment";

    // Act
    List<Token> tokens = lex(input);

    // Assert
    assertEquals(2, tokens.size());
    assertEquals(TokenType.WHITE_SPACE, tokens.get(0).type);
    assertEquals(COMMENT, tokens.get(1).type);
  }

  // ---- patterns + owner classification ----

  @Test
  public void lex_simpleRule_emitsPatternThenUserOwner() {
    // Arrange
    String input = "* @alice";
    List<Token> expected = List.of(tok(PATTERN, "*"), tok(USER_OWNER, "@alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_ownerContainingSlash_classifiesAsTeamOwner() {
    // Arrange
    String input = "*.java @org/java-team";
    List<Token> expected = List.of(
            tok(PATTERN, "*.java"),
            tok(TEAM_OWNER, "@org/java-team"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_doubleAtMaintainer_classifiesAsRoleOwner() {
    // Arrange
    String input = "/backend/** @@maintainer";
    List<Token> expected = List.of(
            tok(PATTERN, "/backend/**"),
            tok(ROLE_OWNER, "@@maintainer"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_doubleAtDeveloper_classifiesAsRoleOwner() {
    // Arrange
    String input = "* @@developer";

    // Act
    IElementType ownerType = significant(input).get(1).type;

    // Assert
    assertEquals(ROLE_OWNER, ownerType);
  }

  @Test
  public void lex_doubleAtDevelopers_classifiesAsRoleOwner() {
    // Arrange
    String input = "* @@developers";

    // Act
    IElementType ownerType = significant(input).get(1).type;

    // Assert
    assertEquals(ROLE_OWNER, ownerType);
  }

  @Test
  public void lex_emailLikeOwner_classifiesAsEmailOwner() {
    // Arrange
    String input = "docs/* tech-writer@example.com";
    List<Token> expected = List.of(
            tok(PATTERN, "docs/*"),
            tok(EMAIL_OWNER, "tech-writer@example.com"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_mixedOwnerKindsOnSameLine_classifiesEachIndividually() {
    // Arrange
    String input = "*.js @org/js-team js-lead@example.com";
    List<Token> expected = List.of(
            tok(PATTERN, "*.js"),
            tok(TEAM_OWNER, "@org/js-team"),
            tok(EMAIL_OWNER, "js-lead@example.com"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_wildcardWithTwoUserOwners_emitsBothAsUserOwner() {
    // Arrange
    String input = "* @global-owner1 @global-owner2";
    List<Token> expected = List.of(
            tok(PATTERN, "*"),
            tok(USER_OWNER, "@global-owner1"),
            tok(USER_OWNER, "@global-owner2"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_leadingWhitespaceBeforePattern_skipsToPattern() {
    // Arrange
    String input = "   * @alice";
    List<Token> expected = List.of(tok(PATTERN, "*"), tok(USER_OWNER, "@alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_tabsBetweenTokens_classifyAsWhitespace() {
    // Arrange
    String input = "*\t@alice";
    List<Token> expected = List.of(tok(PATTERN, "*"), tok(USER_OWNER, "@alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_negationPatternWithoutOwners_emitsAsSinglePattern() {
    // Arrange
    String input = "!/config/**/*.rb\n";
    List<Token> expected = List.of(tok(PATTERN, "!/config/**/*.rb"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_fileWithoutTrailingNewline_emitsLastTokenFully() {
    // Arrange
    String input = "* @alice";
    List<Token> expected = List.of(tok(PATTERN, "*"), tok(USER_OWNER, "@alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  // ---- bad characters in owner position ----

  @Test
  public void lex_bareWordInOwnerPosition_classifiesAsBadCharacter() {
    // Arrange
    String input = "* alice";
    List<Token> expected = List.of(tok(PATTERN, "*"), tok(BAD_CHARACTER, "alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_loneAtSignInOwnerPosition_classifiesAsUserOwner() {
    // Arrange — "@" alone — startsWith("@@") false, contains("/") false → USER_OWNER.
    String input = "* @";
    List<Token> expected = List.of(tok(PATTERN, "*"), tok(USER_OWNER, "@"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  // ---- sections ----

  @Test
  public void lex_sectionHeaderAlone_emitsAsSectionHeader() {
    // Arrange
    String input = "[Backend]";
    List<Token> expected = List.of(tok(SECTION_HEADER, "[Backend]"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_optionalSectionHeader_emitsCaretAndBracketsAsOneToken() {
    // Arrange
    String input = "^[Frontend]";
    List<Token> expected = List.of(tok(SECTION_HEADER, "^[Frontend]"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_sectionHeaderFollowedByApprovalCount_emitsBothTokens() {
    // Arrange
    String input = "[Backend][2]";
    List<Token> expected = List.of(
            tok(SECTION_HEADER, "[Backend]"),
            tok(APPROVAL_COUNT, "[2]"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_sectionWithApprovalCountAndDefaultOwners_emitsAllFourTokens() {
    // Arrange
    String input = "[Backend][2] @org/backend @alice";
    List<Token> expected = List.of(
            tok(SECTION_HEADER, "[Backend]"),
            tok(APPROVAL_COUNT, "[2]"),
            tok(TEAM_OWNER, "@org/backend"),
            tok(USER_OWNER, "@alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_sectionWithOwnersButNoApprovalCount_emitsSectionThenOwner() {
    // Arrange
    String input = "[Backend] @alice";
    List<Token> expected = List.of(
            tok(SECTION_HEADER, "[Backend]"),
            tok(USER_OWNER, "@alice"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_approvalCountAfterWhitespace_emitsAsApprovalCount() {
    // Arrange
    String input = "[Backend]   [2]";
    List<Token> expected = List.of(
            tok(SECTION_HEADER, "[Backend]"),
            tok(APPROVAL_COUNT, "[2]"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_unclosedSectionBracket_consumesToEndOfLine() {
    // Arrange
    String input = "[Backend\n";
    List<Token> expected = List.of(tok(SECTION_HEADER, "[Backend"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_bracketsOnNewLineAfterSection_classifyAsSectionHeader() {
    // Arrange — after the newline, lexer returns to LINE_START — "[2]" is then
    // SECTION_HEADER, not APPROVAL_COUNT.
    String input = "[Backend]\n[2]";
    List<Token> expected = List.of(
            tok(SECTION_HEADER, "[Backend]"),
            tok(SECTION_HEADER, "[2]"));

    // Act
    List<Token> actual = significant(input);

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  public void lex_bracketsAtLineStart_classifyAsSectionHeader() {
    // Arrange
    String input = "[2]";

    // Act
    IElementType type = significant(input).getFirst().type;

    // Assert
    assertEquals(SECTION_HEADER, type);
  }

  @Test
  public void lex_bracketsAfterSectionHeader_classifyAsApprovalCount() {
    // Arrange
    String input = "[A][2]";

    // Act
    IElementType type = significant(input).get(1).type;

    // Assert
    assertEquals(APPROVAL_COUNT, type);
    assertNotEquals(SECTION_HEADER, type);
  }

  // ---- realistic content ----

  @Test
  public void lex_settingsPageDemoText_producesNoBadCharacterTokens() {
    // Arrange
    String demo = """
            # Global owner
            * @global-owner1 @global-owner2
            *.java   @org/java-team
            *.js     @org/js-team   js-lead@example.com
            /build/logs/  @doctocat
            docs/**/*.md  @org/docs  tech-writer@example.com
            !/config/**/*.rb
            [Backend][2] @org/backend  @alice
            /backend/api/**  @@maintainer
            ^[Frontend][1] @org/frontend
            /frontend/**  @bob  @carol
            """;

    // Act
    List<Token> tokens = lex(demo);

    // Assert
    for (Token t : tokens) {
      if (t.type == BAD_CHARACTER) {
        throw new AssertionError("Unexpected BAD_CHARACTER: " + t.text);
      }
    }
  }

  // ---- structural invariants ----

  @Test
  public void lex_tokenOffsets_areContiguousAndCoverEntireInput() {
    // Arrange
    String input = "* @alice\n[Section][2] @bob\n";
    CodeownersLexer lexer = new CodeownersLexer();

    // Act
    lexer.start(input, 0, input.length(), 0);
    List<int[]> ranges = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      ranges.add(new int[]{lexer.getTokenStart(), lexer.getTokenEnd()});
      lexer.advance();
    }

    // Assert
    int expectedStart = 0;
    for (int[] range : ranges) {
      assertEquals(expectedStart, range[0]);
      expectedStart = range[1];
    }
    assertEquals(input.length(), expectedStart);
  }

  @Test
  public void start_inputBuffer_isExposedThroughGetBufferSequence() {
    // Arrange
    String input = "* @alice";
    CodeownersLexer lexer = new CodeownersLexer();

    // Act
    lexer.start(input, 0, input.length(), 0);

    // Assert
    assertEquals(input, lexer.getBufferSequence().toString());
    assertEquals(input.length(), lexer.getBufferEnd());
  }

  private record Token(IElementType type, String text) {
  }
}
