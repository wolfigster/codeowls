package net.wolfig.codeowls.matcher;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersRule} — verifies the record fields and the
 * {@link CodeownersRule#matches} delegation to the compiled pattern.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersRuleTest {

  private static CodeownersRule rule(String pattern, List<String> owners, int line) {
    return new CodeownersRule(pattern, owners, CodeownersGlob.compile(pattern), null, line);
  }

  @Test
  public void matches_pathMatchingPattern_returnsTrue() {
    // Arrange
    CodeownersRule rule = rule("*.java", List.of("@backend"), 0);

    // Act
    boolean result = rule.matches("Foo.java");

    // Assert
    assertTrue(result);
  }

  @Test
  public void matches_pathMatchingPatternInSubdirectory_returnsTrue() {
    // Arrange
    CodeownersRule rule = rule("*.java", List.of("@backend"), 0);

    // Act
    boolean result = rule.matches("src/Foo.java");

    // Assert
    assertTrue(result);
  }

  @Test
  public void matches_pathNotMatchingPattern_returnsFalse() {
    // Arrange
    CodeownersRule rule = rule("*.java", List.of("@backend"), 0);

    // Act
    boolean result = rule.matches("README.md");

    // Assert
    assertFalse(result);
  }

  @Test
  public void pattern_afterConstruction_returnsProvidedValue() {
    // Arrange
    CodeownersRule rule = rule("/CODEOWNERS", List.of("@a", "@org/b"), 42);

    // Act
    String pattern = rule.pattern();

    // Assert
    assertEquals("/CODEOWNERS", pattern);
  }

  @Test
  public void owners_afterConstruction_returnsProvidedList() {
    // Arrange
    List<String> owners = List.of("@a", "@org/b");
    CodeownersRule rule = rule("/CODEOWNERS", owners, 42);

    // Act
    List<String> actual = rule.owners();

    // Assert
    assertEquals(owners, actual);
  }

  @Test
  public void lineNumber_afterConstruction_returnsProvidedValue() {
    // Arrange
    CodeownersRule rule = rule("/CODEOWNERS", List.of("@a"), 42);

    // Act
    int lineNumber = rule.lineNumber();

    // Assert
    assertEquals(42, lineNumber);
  }
}
