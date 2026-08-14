package net.wolfig.codeowls.entry;

import net.wolfig.codeowls.entry.CodeownersEntryRule.PathMode;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests rule generation and validation through the production lexer/parser.
 */
public class CodeownersEntryRuleTest {

  @Test
  public void pattern_exactPath_isRepositoryRootRelative() {
    assertEquals("/src/main/App.java",
            CodeownersEntryRule.pattern("src/main/App.java", PathMode.EXACT));
  }

  @Test
  public void pattern_windowsPath_usesForwardSlashes() {
    assertEquals("/src/main/App.java",
            CodeownersEntryRule.pattern("src\\main\\App.java", PathMode.EXACT));
  }

  @Test
  public void pattern_fileNameOnly_dropsDirectories() {
    assertEquals("App.java",
            CodeownersEntryRule.pattern("src/main/App.java", PathMode.FILE_NAME));
  }

  @Test
  public void build_singleOwner_createsParseableRule() {
    assertEquals("/src/App.java @alice",
            CodeownersEntryRule.build("/src/App.java", "@alice"));
  }

  @Test
  public void build_multipleOwners_normalizesWhitespace() {
    assertEquals("/src/App.java @payment-team @alice",
            CodeownersEntryRule.build("/src/App.java", " @payment-team   @alice "));
  }

  @Test
  public void build_emailOwner_isAccepted() {
    assertEquals("/src/App.java team@example.com",
            CodeownersEntryRule.build("/src/App.java", "team@example.com"));
  }

  @Test
  public void build_gitLabRoleOwner_isAccepted() {
    assertEquals("/src/App.java @@maintainer",
            CodeownersEntryRule.build("/src/App.java", "@@maintainer"));
  }

  @Test
  public void build_invalidOwner_isRejected() {
    assertNull(CodeownersEntryRule.build("/src/App.java", "not-an-owner"));
    assertNull(CodeownersEntryRule.build("/src/App.java", ""));
  }

  @Test
  public void build_resultIsParsedByExistingRuleParser() {
    String text = CodeownersEntryRule.build(
            "/src/App.java", "@alice team@example.com @@maintainer");
    assertNotNull(text);

    List<CodeownersRule> rules = CodeownersRuleParser.parse(text, null);

    assertEquals(1, rules.size());
    assertEquals("/src/App.java", rules.getFirst().pattern());
    assertEquals(List.of("@alice", "team@example.com", "@@maintainer"),
            rules.getFirst().owners());
  }

  @Test
  public void existingExactRule_returnsLastExactPatternOnly() {
    List<CodeownersRule> rules = CodeownersRuleParser.parse(
            """
                    /src/** @broad
                    /src/App.java @first
                    *.java @later-broad
                    /src/App.java @second
                    """, null);

    CodeownersRule exact =
            CodeownersEntryRule.existingExactRule(rules, "/src/App.java");

    assertNotNull(exact);
    assertEquals(List.of("@second"), exact.owners());
    assertEquals(3, exact.lineNumber());
    assertNull(CodeownersEntryRule.existingExactRule(rules, "/src/Other.java"));
  }
}
