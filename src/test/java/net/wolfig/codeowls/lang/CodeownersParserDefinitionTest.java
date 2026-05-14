package net.wolfig.codeowls.lang;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import net.wolfig.codeowls.lexer.CodeownersLexer;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersParserDefinition}.
 *
 * <p>The parser definition itself is intentionally minimal — it wires the
 * lexer to the language and declares the platform-level token sets (whitespace,
 * comments, string literals). These tests exercise only that wiring and the
 * token-set declarations; the trivial body of {@code createParser} is left to
 * {@code ParsingTestCase}-style integration coverage.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersParserDefinitionTest {

  @Test
  public void createLexer_returnsCodeownersLexer() {
    // Arrange
    CodeownersParserDefinition def = new CodeownersParserDefinition();

    // Act
    Lexer lexer = def.createLexer(null);

    // Assert
    assertNotNull(lexer);
    assertTrue(lexer instanceof CodeownersLexer);
  }

  @Test
  public void createParser_returnsNonNullParser() {
    // Arrange
    CodeownersParserDefinition def = new CodeownersParserDefinition();

    // Act
    PsiParser parser = def.createParser(null);

    // Assert
    assertNotNull(parser);
  }

  @Test
  public void fileNodeType_isExposedConstant() {
    // Arrange
    CodeownersParserDefinition def = new CodeownersParserDefinition();

    // Act
    IFileElementType fileNodeType = def.getFileNodeType();

    // Assert
    assertSame(CodeownersParserDefinition.FILE, fileNodeType);
    assertSame(CodeownersLanguage.INSTANCE, fileNodeType.getLanguage());
  }

  @Test
  public void whitespaceTokens_isPlatformDefault() {
    // Arrange
    CodeownersParserDefinition def = new CodeownersParserDefinition();

    // Act
    TokenSet whitespace = def.getWhitespaceTokens();

    // Assert
    assertSame(TokenSet.WHITE_SPACE, whitespace);
  }

  @Test
  public void commentTokens_containCommentTokenType() {
    // Arrange
    CodeownersParserDefinition def = new CodeownersParserDefinition();

    // Act
    TokenSet comments = def.getCommentTokens();

    // Assert
    assertTrue(comments.contains(CodeownersTokenTypes.COMMENT));
  }

  @Test
  public void commentTokens_doNotContainOtherTokenTypes() {
    // Arrange
    CodeownersParserDefinition def = new CodeownersParserDefinition();

    // Act
    TokenSet comments = def.getCommentTokens();

    // Assert
    assertFalse(comments.contains(CodeownersTokenTypes.PATTERN));
    assertFalse(comments.contains(CodeownersTokenTypes.USER_OWNER));
    assertFalse(comments.contains(TokenType.WHITE_SPACE));
  }

  @Test
  public void stringLiteralElements_isEmpty() {
    // Arrange
    CodeownersParserDefinition def = new CodeownersParserDefinition();

    // Act
    TokenSet stringLiterals = def.getStringLiteralElements();

    // Assert
    assertSame(TokenSet.EMPTY, stringLiterals);
  }
}
