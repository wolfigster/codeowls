package net.wolfig.codeowls.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersSyntaxHighlighterFactory}.
 *
 * <p>Confirms that the factory produces a {@link CodeownersSyntaxHighlighter}
 * regardless of the (nullable) project and virtual-file arguments, and that
 * each call returns a fresh instance rather than a shared singleton.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersSyntaxHighlighterFactoryTest {

  @Test
  public void returnsCodeownersSyntaxHighlighter_forNullProjectAndFile() {
    // Arrange
    CodeownersSyntaxHighlighterFactory factory = new CodeownersSyntaxHighlighterFactory();

    // Act
    SyntaxHighlighter highlighter = factory.getSyntaxHighlighter(null, null);

    // Assert
    assertNotNull(highlighter);
    assertTrue(highlighter instanceof CodeownersSyntaxHighlighter);
  }

  @Test
  public void returnsFreshInstance_eachCall() {
    // Arrange
    CodeownersSyntaxHighlighterFactory factory = new CodeownersSyntaxHighlighterFactory();

    // Act
    SyntaxHighlighter first = factory.getSyntaxHighlighter(null, null);
    SyntaxHighlighter second = factory.getSyntaxHighlighter(null, null);

    // Assert
    assertNotSame(first, second);
  }
}
