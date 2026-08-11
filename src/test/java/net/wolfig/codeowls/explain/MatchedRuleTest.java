package net.wolfig.codeowls.explain;

import net.wolfig.codeowls.matcher.CodeownersGlob;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersSection;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link MatchedRule} — a thin wrapper over {@link CodeownersRule}
 * that adds the {@code effective} flag and delegates every other accessor to
 * the wrapped rule. These tests pin that delegation so the wrapper cannot
 * silently diverge from the rule model.
 *
 * <p>Pure JUnit — no IntelliJ platform needed ({@link CodeownersRule} accepts a
 * {@code null} source file).
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class MatchedRuleTest {

  private static CodeownersRule ruleInSection(String pattern, List<String> owners,
                                              Integer approvalCount, CodeownersSection section,
                                              boolean ownersInherited) {
    return new CodeownersRule(pattern, owners, CodeownersGlob.compile(pattern),
            null, 7, approvalCount, section, ownersInherited);
  }

  @Test
  public void accessors_delegateToWrappedRule() {
    // Arrange
    CodeownersSection section = new CodeownersSection("Backend", false, List.of("@backend-team"), 2);
    CodeownersRule rule = ruleInSection("/src/**", List.of("@backend-team"), 2, section, true);

    // Act
    MatchedRule matched = new MatchedRule(rule, true);

    // Assert
    assertSame(rule, matched.rule());
    assertEquals("/src/**", matched.pattern());
    assertEquals(List.of("@backend-team"), matched.resolvedOwners());
    assertEquals(Integer.valueOf(2), matched.approvalCount());
    assertNull(matched.sourceFile());
    assertEquals(7, matched.line());
    assertSame(section, matched.section());
    assertTrue(matched.inheritedFromSection());
  }

  @Test
  public void effective_reflectsConstructorFlag() {
    // Arrange
    CodeownersRule rule = ruleInSection("*.java", List.of("@backend"), null, null, false);

    // Assert
    assertTrue(new MatchedRule(rule, true).effective());
    assertFalse(new MatchedRule(rule, false).effective());
  }

  @Test
  public void ruleOutsideSection_hasNullSectionAndNotInherited() {
    // Arrange — a plain rule with no enclosing section.
    CodeownersRule rule = new CodeownersRule("*.java", List.of("@backend"),
            CodeownersGlob.compile("*.java"), null, 0);

    // Act
    MatchedRule matched = new MatchedRule(rule, true);

    // Assert
    assertNull(matched.section());
    assertFalse(matched.inheritedFromSection());
    assertNull(matched.approvalCount());
  }
}
