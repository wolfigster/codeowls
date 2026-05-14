package net.wolfig.codeowls.statusbar;

import net.wolfig.codeowls.matcher.CodeownersGlob;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersOwnerResolution} — covers the {@link
 * CodeownersOwnerResolution#NONE} sentinel and the {@code owners()} pass-through.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersOwnerResolutionTest {

  private static CodeownersRule rule(String pattern, List<String> owners) {
    return new CodeownersRule(pattern, owners, CodeownersGlob.compile(pattern), null, 0);
  }

  @Test
  public void isEmpty_NONE_returnsTrue() {
    // Arrange
    CodeownersOwnerResolution res = CodeownersOwnerResolution.NONE;

    // Act
    boolean empty = res.isEmpty();

    // Assert
    assertTrue(empty);
  }

  @Test
  public void rule_NONE_returnsNull() {
    // Arrange
    CodeownersOwnerResolution res = CodeownersOwnerResolution.NONE;

    // Act
    CodeownersRule rule = res.rule();

    // Assert
    assertNull(rule);
  }

  @Test
  public void owners_NONE_returnsEmptyList() {
    // Arrange
    CodeownersOwnerResolution res = CodeownersOwnerResolution.NONE;

    // Act
    List<String> owners = res.owners();

    // Assert
    assertTrue(owners.isEmpty());
  }

  @Test
  public void isEmpty_resolutionWithRule_returnsFalse() {
    // Arrange
    CodeownersOwnerResolution res = new CodeownersOwnerResolution(
            rule("*.java", List.of("@backend", "@john")));

    // Act
    boolean empty = res.isEmpty();

    // Assert
    assertFalse(empty);
  }

  @Test
  public void rule_resolutionWithRule_returnsProvidedRule() {
    // Arrange
    CodeownersRule provided = rule("*.java", List.of("@backend", "@john"));
    CodeownersOwnerResolution res = new CodeownersOwnerResolution(provided);

    // Act
    CodeownersRule actual = res.rule();

    // Assert
    assertSame(provided, actual);
  }

  @Test
  public void owners_resolutionWithRule_returnsRuleOwners() {
    // Arrange
    CodeownersOwnerResolution res = new CodeownersOwnerResolution(
            rule("*.java", List.of("@backend", "@john")));

    // Act
    List<String> owners = res.owners();

    // Assert
    assertEquals(List.of("@backend", "@john"), owners);
  }
}
