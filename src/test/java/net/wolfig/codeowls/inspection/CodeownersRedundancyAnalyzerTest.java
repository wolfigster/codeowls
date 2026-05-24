package net.wolfig.codeowls.inspection;

import net.wolfig.codeowls.inspection.CodeownersRedundancyAnalyzer.Finding;
import net.wolfig.codeowls.inspection.CodeownersRedundancyAnalyzer.Kind;
import net.wolfig.codeowls.matcher.CodeownersGlob;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CodeownersRedundancyAnalyzer} — the pure "which rules are
 * unnecessary" computation over a list of rules and project file paths.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersRedundancyAnalyzerTest {

  private static CodeownersRule rule(String pattern) {
    return new CodeownersRule(
            pattern, List.of("@owner"), CodeownersGlob.compile(pattern), null, 0);
  }

  private static List<Finding> analyze(List<String> patterns, List<String> files) {
    return CodeownersRedundancyAnalyzer.analyze(patterns.stream().map(CodeownersRedundancyAnalyzerTest::rule).toList(), files);
  }

  @Test
  public void analyze_patternMatchingNoFile_flagsNoFilesMatch() {
    // Arrange — a rule for a file type the project doesn't contain.
    List<Finding> findings = analyze(List.of("*.kt"), List.of("Foo.java", "Bar.java"));

    // Assert
    assertEquals(List.of(new Finding(0, Kind.NO_FILES_MATCH, -1)), findings);
  }

  @Test
  public void analyze_exactPathThatDoesNotExist_flagsNoFilesMatch() {
    // Arrange — an anchored path with no corresponding file.
    List<Finding> findings = analyze(List.of("/src/Gone.java"), List.of("src/Here.java"));

    // Assert
    assertEquals(List.of(new Finding(0, Kind.NO_FILES_MATCH, -1)), findings);
  }

  @Test
  public void analyze_duplicatePattern_flagsEarlierAsShadowedByLater() {
    // Arrange — two identical patterns; last-match-wins kills the first.
    List<Finding> findings = analyze(List.of("*.java", "*.java"), List.of("Foo.java"));

    // Assert — only rule 0 is unnecessary, shadowed by rule 1.
    assertEquals(List.of(new Finding(0, Kind.SHADOWED, 1)), findings);
  }

  @Test
  public void analyze_specificRuleFollowedByCatchAll_flagsSpecificAsShadowed() {
    // Arrange — a global wildcard placed last overrides everything above it.
    List<Finding> findings = analyze(
            List.of("/src/Foo.java", "*"),
            List.of("src/Foo.java", "README.md"));

    // Assert — the specific rule never wins; the catch-all does.
    assertEquals(List.of(new Finding(0, Kind.SHADOWED, 1)), findings);
  }

  @Test
  public void analyze_ruleThatWinsForSomeFiles_isNotFlagged() {
    // Arrange — "*.java" overlaps "/src/**" for src files, but still wins for
    // java files outside src, so it remains necessary.
    List<Finding> findings = analyze(
            List.of("*.java", "/src/**"),
            List.of("src/Foo.java", "Top.java"));

    // Assert
    assertTrue("no rule should be flagged: " + findings, findings.isEmpty());
  }

  @Test
  public void analyze_negationPattern_isIgnored() {
    // Arrange — negation patterns are not analysed (the glob strips '!').
    List<Finding> findings = analyze(List.of("!/secret/**"), List.of("src/Foo.java"));

    // Assert
    assertTrue("negation rule must not be flagged: " + findings, findings.isEmpty());
  }

  @Test
  public void analyze_allRulesEffective_returnsNoFindings() {
    // Arrange — two disjoint, each-matching rules.
    List<Finding> findings = analyze(
            List.of("*.java", "*.md"),
            List.of("Foo.java", "README.md"));

    // Assert
    assertTrue(findings.isEmpty());
  }

  @Test
  public void analyze_noRules_returnsNoFindings() {
    // Act / Assert
    assertTrue(CodeownersRedundancyAnalyzer.analyze(List.of(), List.of("Foo.java")).isEmpty());
  }
}
