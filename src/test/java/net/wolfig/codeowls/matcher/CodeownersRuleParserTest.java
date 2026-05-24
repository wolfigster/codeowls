package net.wolfig.codeowls.matcher;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CodeownersRuleParser} — drives the parser over small
 * CODEOWNERS snippets and asserts the resulting rule list. The parser
 * delegates tokenisation to the shared lexer, so these tests focus on the
 * grouping logic (pattern + owners per line, line-number tracking, ignoring
 * comments/sections/blank lines).
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersRuleParserTest {

  @Test
  public void parse_emptyInput_returnsEmptyList() {
    // Arrange
    String input = "";

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertTrue(rules.isEmpty());
  }

  @Test
  public void parse_singleRuleLine_returnsOneRuleWithCorrectFields() {
    // Arrange
    String input = "*.java @backend\n";

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(1, rules.size());
    assertEquals("*.java", rules.getFirst().pattern());
    assertEquals(List.of("@backend"), rules.getFirst().owners());
    assertEquals(0, rules.getFirst().lineNumber());
  }

  @Test
  public void parse_multipleOwnersOnLine_capturesEachInOrder() {
    // Arrange
    String input = "*.java @backend-team @john\n";

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(List.of("@backend-team", "@john"), rules.getFirst().owners());
  }

  @Test
  public void parse_mixedOwnerKinds_capturesAllAsOwners() {
    // Arrange — user, team, role, e-mail.
    String input = "* @alice @org/team @@maintainer alice@example.com\n";

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(
            List.of("@alice", "@org/team", "@@maintainer", "alice@example.com"),
            rules.getFirst().owners());
  }

  @Test
  public void parse_inputWithCommentsAndBlanks_extractsOnlyRuleLines() {
    // Arrange
    String input = """
            # header comment
            
            *.java @backend
            # trailing comment
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(1, rules.size());
    assertEquals("*.java", rules.getFirst().pattern());
  }

  @Test
  public void parse_multipleRules_preservesDocumentOrder() {
    // Arrange
    String input = """
            *.java @backend
            *.kt @android
            /CODEOWNERS @admins
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(3, rules.size());
    assertEquals("*.java", rules.get(0).pattern());
    assertEquals("*.kt", rules.get(1).pattern());
    assertEquals("/CODEOWNERS", rules.get(2).pattern());
  }

  @Test
  public void parse_ruleAfterBlankAndComment_recordsZeroBasedLineNumber() {
    // Arrange
    String input = """
            # a
            
            *.java @backend
            *.kt @android
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(2, rules.get(0).lineNumber());
    assertEquals(3, rules.get(1).lineNumber());
  }

  @Test
  public void parse_ruleWithoutTrailingNewline_stillCommitsTheRule() {
    // Arrange
    String input = "*.java @backend";

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(1, rules.size());
    assertEquals(List.of("@backend"), rules.getFirst().owners());
  }

  @Test
  public void parse_inputContainingSectionHeader_extractsRuleAndOwnRuleOwnersOverrideDefault() {
    // Arrange — GitLab section syntax. The rule names its own owner, which
    // overrides the section default; the header itself produces no rule.
    String input = """
            [Backend][2] @org/backend
            src/*.java @backend
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(1, rules.size());
    assertEquals("src/*.java", rules.getFirst().pattern());
    assertEquals(List.of("@backend"), rules.getFirst().owners());
  }

  @Test
  public void parse_ruleWithoutOwnersInSection_inheritsSectionDefault() {
    // Arrange — the GitLab "default code owner for a section" example: rules
    // with no owners of their own inherit the section's default owner.
    String input = """
            [Documentation] @docs-team
            docs/
            README.md
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(2, rules.size());
    assertEquals("docs/", rules.get(0).pattern());
    assertEquals(List.of("@docs-team"), rules.get(0).owners());
    assertEquals("README.md", rules.get(1).pattern());
    assertEquals(List.of("@docs-team"), rules.get(1).owners());
  }

  @Test
  public void parse_ruleWithOwnersInSection_overridesSectionDefault() {
    // Arrange — a rule that lists owners replaces the section default entirely;
    // a sibling rule with no owners still inherits it.
    String input = """
            [Database] @database-team @agarcia
            model/db/
            config/db/database-setup.md @docs-team
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(2, rules.size());
    assertEquals(List.of("@database-team", "@agarcia"), rules.get(0).owners());
    assertEquals("config/db/database-setup.md", rules.get(1).pattern());
    assertEquals(List.of("@docs-team"), rules.get(1).owners());
  }

  @Test
  public void parse_sectionDefaultWithApprovalCount_inheritedByBareRule() {
    // Arrange — the optional [N] approval count between the section name and
    // the default owners must not be mistaken for an owner.
    String input = """
            [Backend][2] @org/backend @alice
            src/Main.java
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(1, rules.size());
    assertEquals(List.of("@org/backend", "@alice"), rules.getFirst().owners());
  }

  @Test
  public void parse_ruleInLaterSection_doesNotInheritEarlierSectionDefault() {
    // Arrange — a section's default scope ends at the next header. The second
    // section declares no defaults, so its bare rule has no owners.
    String input = """
            [Documentation] @docs-team
            docs/
            [Misc]
            scripts/
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(2, rules.size());
    assertEquals(List.of("@docs-team"), rules.get(0).owners());
    assertEquals("scripts/", rules.get(1).pattern());
    assertTrue(rules.get(1).owners().isEmpty());
  }

  @Test
  public void parse_ruleBeforeAnySection_doesNotInheritLaterSectionDefault() {
    // Arrange — defaults apply only inside their section; a leading rule keeps
    // its own (here empty) owners.
    String input = """
            *.txt
            [Docs] @docs-team
            README.md
            """;

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(2, rules.size());
    assertEquals("*.txt", rules.get(0).pattern());
    assertTrue(rules.get(0).owners().isEmpty());
    assertEquals(List.of("@docs-team"), rules.get(1).owners());
  }

  @Test
  public void parse_ruleWithoutOwners_stillEmitsTheRule() {
    // Arrange — a pattern without explicit owners (e.g. clearing a parent rule).
    String input = "*.txt\n";

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    assertEquals(1, rules.size());
    assertEquals("*.txt", rules.getFirst().pattern());
    assertTrue(rules.getFirst().owners().isEmpty());
  }

  @Test
  public void parse_ruleWithGlob_producesAppliedCompiledPattern() {
    // Arrange
    String input = "src/**/*.java @backend\n";

    // Act
    List<CodeownersRule> rules = CodeownersRuleParser.parse(input, null);

    // Assert
    CodeownersRule rule = rules.getFirst();
    assertTrue(rule.matches("src/Foo.java"));
    assertTrue(rule.matches("src/main/java/Foo.java"));
  }
}
