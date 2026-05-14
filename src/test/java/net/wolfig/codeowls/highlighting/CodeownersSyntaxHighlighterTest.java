package net.wolfig.codeowls.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import net.wolfig.codeowls.lexer.CodeownersLexer;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersSyntaxHighlighter}.
 *
 * <p>Verifies the {@code IElementType → TextAttributesKey} mapping for every
 * token category produced by {@link CodeownersLexer}, the whitespace passthrough,
 * and the empty-array fallback for tokens the highlighter does not recognize.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersSyntaxHighlighterTest {

  /**
   * Shared Arrange + Act + Assert for the trivial one-to-one mapping cases.
   */
  private static void assertSingleKey(TextAttributesKey expected, IElementType tokenType) {
    // Arrange
    CodeownersSyntaxHighlighter highlighter = new CodeownersSyntaxHighlighter();

    // Act
    TextAttributesKey[] keys = highlighter.getTokenHighlights(tokenType);

    // Assert
    assertEquals(1, keys.length);
    assertSame(expected, keys[0]);
  }

  @Test
  public void getHighlightingLexer_default_returnsCodeownersLexer() {
    // Arrange
    CodeownersSyntaxHighlighter highlighter = new CodeownersSyntaxHighlighter();

    // Act
    Lexer lexer = highlighter.getHighlightingLexer();

    // Assert
    assertNotNull(lexer);
    assertTrue(lexer instanceof CodeownersLexer);
  }

  @Test
  public void getTokenHighlights_commentToken_returnsCommentColor() {
    assertSingleKey(CodeownersHighlightingColors.COMMENT, CodeownersTokenTypes.COMMENT);
  }

  @Test
  public void getTokenHighlights_patternToken_returnsPatternColor() {
    assertSingleKey(CodeownersHighlightingColors.PATTERN, CodeownersTokenTypes.PATTERN);
  }

  @Test
  public void getTokenHighlights_userOwnerToken_returnsUserOwnerColor() {
    assertSingleKey(CodeownersHighlightingColors.USER_OWNER, CodeownersTokenTypes.USER_OWNER);
  }

  @Test
  public void getTokenHighlights_teamOwnerToken_returnsTeamOwnerColor() {
    assertSingleKey(CodeownersHighlightingColors.TEAM_OWNER, CodeownersTokenTypes.TEAM_OWNER);
  }

  @Test
  public void getTokenHighlights_roleOwnerToken_returnsRoleOwnerColor() {
    assertSingleKey(CodeownersHighlightingColors.ROLE_OWNER, CodeownersTokenTypes.ROLE_OWNER);
  }

  @Test
  public void getTokenHighlights_emailOwnerToken_returnsEmailOwnerColor() {
    assertSingleKey(CodeownersHighlightingColors.EMAIL_OWNER, CodeownersTokenTypes.EMAIL_OWNER);
  }

  @Test
  public void getTokenHighlights_sectionHeaderToken_returnsSectionHeaderColor() {
    assertSingleKey(CodeownersHighlightingColors.SECTION_HEADER, CodeownersTokenTypes.SECTION_HEADER);
  }

  @Test
  public void getTokenHighlights_approvalCountToken_returnsApprovalCountColor() {
    assertSingleKey(CodeownersHighlightingColors.APPROVAL_COUNT, CodeownersTokenTypes.APPROVAL_COUNT);
  }

  @Test
  public void getTokenHighlights_badCharacterToken_returnsBadCharacterColor() {
    assertSingleKey(CodeownersHighlightingColors.BAD_CHARACTER, CodeownersTokenTypes.BAD_CHARACTER);
  }

  @Test
  public void getTokenHighlights_whitespaceToken_returnsEmptyArray() {
    // Arrange
    CodeownersSyntaxHighlighter highlighter = new CodeownersSyntaxHighlighter();

    // Act
    TextAttributesKey[] keys = highlighter.getTokenHighlights(TokenType.WHITE_SPACE);

    // Assert
    assertEquals(0, keys.length);
  }

  @Test
  public void getTokenHighlights_unknownTokenType_returnsEmptyArray() {
    // Arrange
    CodeownersSyntaxHighlighter highlighter = new CodeownersSyntaxHighlighter();
    IElementType unknown = new IElementType("UNKNOWN_TEST_TOKEN", null, false) {
    };

    // Act
    TextAttributesKey[] keys = highlighter.getTokenHighlights(unknown);

    // Assert
    assertEquals(0, keys.length);
  }
}
