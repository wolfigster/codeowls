package net.wolfig.codeowls.suggestion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link OwnerSuggestion} — a data record whose only behavior is
 * {@link OwnerSuggestion#confidencePercent()}, which rounds the {@code [0, 1]}
 * confidence to a whole percentage.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class OwnerSuggestionTest {

  private static OwnerSuggestion withConfidence(double confidence) {
    return new OwnerSuggestion("@api-team", "already used in CODEOWNERS", confidence);
  }

  @Test
  public void confidencePercent_zero_isZero() {
    assertEquals(0, withConfidence(0.0).confidencePercent());
  }

  @Test
  public void confidencePercent_one_isHundred() {
    assertEquals(100, withConfidence(1.0).confidencePercent());
  }

  @Test
  public void confidencePercent_half_isFifty() {
    assertEquals(50, withConfidence(0.5).confidencePercent());
  }

  @Test
  public void confidencePercent_roundsToNearestWholePercent() {
    // Arrange / Act / Assert — 78.6% rounds up, 78.4% rounds down.
    assertEquals(79, withConfidence(0.786).confidencePercent());
    assertEquals(78, withConfidence(0.784).confidencePercent());
  }

  @Test
  public void accessors_returnConstructorValues() {
    // Arrange
    OwnerSuggestion suggestion = new OwnerSuggestion("@alice", "git history", 0.42);

    // Assert
    assertEquals("@alice", suggestion.owner());
    assertEquals("git history", suggestion.source());
    assertEquals(0.42, suggestion.confidence(), 1e-9);
  }
}
