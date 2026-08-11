package net.wolfig.codeowls.explain;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import net.wolfig.codeowls.matcher.CodeownersGlob;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link OwnershipExplanation} — the immutable evaluation trace behind
 * the "Explain CODEOWNERS Ownership" action. Focuses on its pure derivation
 * logic: which rule is effective (last-match-wins), the effective owners /
 * approval count, and the empty / no-CODEOWNERS states.
 *
 * <p>Uses {@link LightVirtualFile} for the required file references — none of
 * the exercised methods touch VFS internals, so no fixture is needed.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class OwnershipExplanationTest {

  private static final VirtualFile TARGET = new LightVirtualFile("PaymentService.java");
  private static final VirtualFile CODEOWNERS = new LightVirtualFile("CODEOWNERS");

  private static CodeownersRule rule(String pattern, Integer approvalCount, String... owners) {
    return new CodeownersRule(pattern, List.of(owners), CodeownersGlob.compile(pattern),
            null, 0, approvalCount);
  }

  @Test
  public void of_multipleMatches_marksOnlyTheLastEffectiveAndPreservesOrder() {
    // Arrange
    CodeownersRule first = rule("/src/**", null, "@developers");
    CodeownersRule second = rule("/src/payment/**", null, "@backend");
    CodeownersRule third = rule("/src/payment/*.java", null, "@payment-team");

    // Act
    OwnershipExplanation explanation = OwnershipExplanation.of(
            TARGET, "src/payment/PaymentService.java", CODEOWNERS, List.of(first, second, third));

    // Assert
    List<MatchedRule> matched = explanation.matchedRules();
    assertEquals(3, matched.size());
    assertSame(first, matched.get(0).rule());
    assertSame(second, matched.get(1).rule());
    assertSame(third, matched.get(2).rule());
    assertFalse(matched.get(0).effective());
    assertFalse(matched.get(1).effective());
    assertTrue(matched.get(2).effective());
    assertSame(matched.get(2), explanation.effectiveRule());
  }

  @Test
  public void of_singleMatch_isEffectiveAndDrivesOwnersAndApprovals() {
    // Arrange
    CodeownersRule only = rule("*.java", 2, "@backend", "@alice");

    // Act
    OwnershipExplanation explanation = OwnershipExplanation.of(
            TARGET, "Foo.java", CODEOWNERS, List.of(only));

    // Assert
    assertTrue(explanation.hasMatch());
    assertTrue(explanation.hasCodeownersFile());
    assertTrue(explanation.matchedRules().getFirst().effective());
    assertEquals(List.of("@backend", "@alice"), explanation.effectiveOwners());
    assertEquals(Integer.valueOf(2), explanation.effectiveApprovalCount());
  }

  @Test
  public void of_noMatches_hasCodeownersButNoEffectiveRule() {
    // Arrange — a governing CODEOWNERS file exists, but nothing matched.
    OwnershipExplanation explanation = OwnershipExplanation.of(
            TARGET, "Foo.java", CODEOWNERS, List.of());

    // Assert
    assertTrue(explanation.hasCodeownersFile());
    assertFalse(explanation.hasMatch());
    assertNull(explanation.effectiveRule());
    assertTrue(explanation.effectiveOwners().isEmpty());
    assertNull(explanation.effectiveApprovalCount());
  }

  @Test
  public void noCodeowners_reportsNoFileAndNoMatch() {
    // Act
    OwnershipExplanation explanation = OwnershipExplanation.noCodeowners(TARGET);

    // Assert
    assertSame(TARGET, explanation.targetFile());
    assertNull(explanation.relativePath());
    assertNull(explanation.codeownersFile());
    assertFalse(explanation.hasCodeownersFile());
    assertFalse(explanation.hasMatch());
    assertNull(explanation.effectiveRule());
    assertTrue(explanation.effectiveOwners().isEmpty());
  }

  @Test
  public void of_matchedRules_isImmutable() {
    // Arrange
    OwnershipExplanation explanation = OwnershipExplanation.of(
            TARGET, "Foo.java", CODEOWNERS, List.of(rule("*.java", null, "@backend")));

    // Act / Assert — the exposed list must not be modifiable.
    assertThrows(UnsupportedOperationException.class,
            () -> explanation.matchedRules().clear());
  }
}
