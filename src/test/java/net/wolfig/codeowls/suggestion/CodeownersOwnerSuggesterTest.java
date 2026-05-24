package net.wolfig.codeowls.suggestion;

import net.wolfig.codeowls.completion.CodeownersOwnerCollector;
import net.wolfig.codeowls.completion.CodeownersOwnerCollector.OwnerCandidate;
import net.wolfig.codeowls.matcher.CodeownersGlob;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CodeownersOwnerSuggester} — the pure confidence-scoring and
 * ranking of owner suggestions. Exercises each signal (path proximity, Git
 * author share, source prior) and the blended ordering, without the IntelliJ
 * platform.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersOwnerSuggesterTest {

  private static CodeownersRule rule(String pattern, String owner) {
    return new CodeownersRule(
            pattern, List.of(owner), CodeownersGlob.compile(pattern), null, 0);
  }

  private static OwnerCandidate currentFile(String owner) {
    return new OwnerCandidate(owner, CodeownersOwnerCollector.SOURCE_CURRENT_FILE);
  }

  private static OwnerCandidate gitHistory(String owner) {
    return new OwnerCandidate(owner, CodeownersOwnerCollector.SOURCE_GIT_HISTORY);
  }

  private static OwnerCandidate role(String owner) {
    return new OwnerCandidate(owner, CodeownersOwnerCollector.SOURCE_BUILTIN_ROLE);
  }

  private static OwnerSuggestion find(List<OwnerSuggestion> suggestions, String owner) {
    return suggestions.stream().filter(s -> s.owner().equals(owner)).findFirst().orElseThrow();
  }

  // -- path proximity ------------------------------------------------------

  @Test
  public void suggest_ownerOfNearerSiblingDirectory_outranksOwnerOfFartherSibling() {
    // Arrange — the target file is unowned; @api owns a sibling under src/api,
    // @src owns a more distant sibling under src/core. Neither rule matches the
    // file, so proximity comes from the shared directory prefix.
    List<CodeownersRule> rules = List.of(
            rule("/src/core/**", "@src"),
            rule("/src/api/v1/**", "@api"));
    List<OwnerCandidate> candidates = List.of(currentFile("@src"), currentFile("@api"));

    // Act
    List<OwnerSuggestion> out = CodeownersOwnerSuggester.suggest(
            "src/api/v2/Handler.java", rules, candidates, Map.of());

    // Assert — @api shares src/api (2 of 3 dir segments) vs @src's src (1 of 3).
    assertEquals("@api", out.get(0).owner());
    assertTrue("nearer sibling should beat farther sibling",
            find(out, "@api").confidence() > find(out, "@src").confidence());
  }

  @Test
  public void suggest_ownerWhoseRuleCoversTheFile_scoresMaxProximity() {
    // Arrange — @docs owns the file's directory tree; the rule matches the file.
    List<CodeownersRule> rules = List.of(rule("/docs/", "@docs"));
    List<OwnerCandidate> candidates = List.of(currentFile("@docs"));

    // Act
    OwnerSuggestion docs = find(CodeownersOwnerSuggester.suggest(
            "docs/intro.md", rules, candidates, Map.of()), "@docs");

    // Assert — proximity 1.0 and source prior 1.0: 0.5*1 + 0.2*1 = 0.7.
    assertEquals(0.7, docs.confidence(), 1e-9);
  }

  @Test
  public void suggest_ownerWithNoRelatedRule_getsOnlySourcePrior() {
    // Arrange — @random appears in CODEOWNERS but only for an unrelated path.
    List<CodeownersRule> rules = List.of(rule("/unrelated/**", "@random"));
    List<OwnerCandidate> candidates = List.of(currentFile("@random"));

    // Act — proximity 0, git 0, source prior 1.0 → 0.2 * 1.0 = 0.2.
    OwnerSuggestion random = find(CodeownersOwnerSuggester.suggest(
            "src/Main.java", rules, candidates, Map.of()), "@random");

    // Assert
    assertEquals(0.2, random.confidence(), 1e-9);
  }

  // -- git author share ----------------------------------------------------

  @Test
  public void suggest_gitAuthorOfFile_isBoostedByCommitShare() {
    // Arrange — alice wrote 3 of 4 commits to the file; bob wrote 1.
    List<OwnerCandidate> candidates = List.of(
            gitHistory("alice@corp.com"), gitHistory("bob@corp.com"));
    Map<String, Integer> authors = Map.of("alice@corp.com", 3, "bob@corp.com", 1);

    // Act
    List<OwnerSuggestion> out = CodeownersOwnerSuggester.suggest(
            "src/Main.java", List.of(), candidates, authors);

    // Assert — alice's larger share ranks her first.
    assertEquals("alice@corp.com", out.get(0).owner());
    assertTrue(find(out, "alice@corp.com").confidence() > find(out, "bob@corp.com").confidence());
    // alice: 0.3 * (3/4) + 0.2 * 0.6 (git prior) = 0.225 + 0.12 = 0.345.
    assertEquals(0.345, find(out, "alice@corp.com").confidence(), 1e-9);
  }

  // -- source prior --------------------------------------------------------

  @Test
  public void suggest_withNoOtherSignal_ordersBySourcePrior() {
    // Arrange — none of these owners has a matching rule or Git history.
    List<OwnerCandidate> candidates = List.of(
            role("@@maintainer"), gitHistory("carol@corp.com"), currentFile("@team"));

    // Act
    List<OwnerSuggestion> out = CodeownersOwnerSuggester.suggest(
            "src/Main.java", List.of(), candidates, Map.of());

    // Assert — current-file (1.0) > git (0.6) > role (0.2).
    assertEquals("@team", out.get(0).owner());
    assertEquals("carol@corp.com", out.get(1).owner());
    assertEquals("@@maintainer", out.get(2).owner());
  }

  // -- shape ---------------------------------------------------------------

  @Test
  public void suggest_capsResultsAtMaxSuggestions() {
    // Arrange — more candidates than the cap.
    List<OwnerCandidate> candidates = new ArrayList<>();
    for (int i = 0; i < CodeownersOwnerSuggester.MAX_SUGGESTIONS + 5; i++) {
      candidates.add(gitHistory("dev" + i + "@corp.com"));
    }

    // Act
    List<OwnerSuggestion> out = CodeownersOwnerSuggester.suggest(
            "src/Main.java", List.of(), candidates, Map.of());

    // Assert
    assertEquals(CodeownersOwnerSuggester.MAX_SUGGESTIONS, out.size());
  }

  @Test
  public void suggest_noCandidates_returnsEmpty() {
    // Act / Assert
    assertTrue(CodeownersOwnerSuggester.suggest(
            "src/Main.java", List.of(), List.of(), Map.of()).isEmpty());
  }

  @Test
  public void confidencePercent_roundsToWholePercent() {
    // Arrange
    OwnerSuggestion s = new OwnerSuggestion("@x", "src", 0.345);

    // Act / Assert — 0.345 → 35% (rounded), 0.7 → 70%.
    assertEquals(35, s.confidencePercent());
    assertEquals(70, new OwnerSuggestion("@y", "src", 0.7).confidencePercent());
  }
}
