package net.wolfig.codeowls.completion;

import org.junit.Test;

import static net.wolfig.codeowls.completion.CodeownersCompletionContext.Segment.*;
import static org.junit.Assert.assertEquals;

/**
 * Pure-logic tests for
 * {@link CodeownersCompletionContext#fromLinePrefix(CharSequence)}.
 *
 * <p>Each test feeds the helper exactly the text the editor would supply for
 * "the start of the current line up to the caret" and asserts which segment
 * the caret is in. The second column — {@code typedSegmentText()} — is what
 * the providers feed into the platform's prefix matcher, so it gets checked
 * as part of the assert too.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersCompletionContextTest {

  @Test
  public void fromLinePrefix_emptyLine_isPattern() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("");

    // Assert
    assertEquals(PATTERN, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_leadingWhitespaceOnly_isPattern() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("   ");

    // Assert
    assertEquals(PATTERN, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_partialPattern_isPatternWithTypedText() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("src/ma");

    // Assert
    assertEquals(PATTERN, ctx.segment());
    assertEquals("src/ma", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_leadingDot_isPatternWithTypedDot() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix(".gi");

    // Assert
    assertEquals(PATTERN, ctx.segment());
    assertEquals(".gi", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_wildcardPattern_isPatternWithTypedWildcard() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("*.j");

    // Assert
    assertEquals(PATTERN, ctx.segment());
    assertEquals("*.j", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_patternFollowedByWhitespace_isOwnerWithEmptyText() {
    // Arrange — pattern + space puts the caret at the start of the owner gap.
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("/src/main/ ");

    // Assert
    assertEquals(OWNER, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_partialOwnerAfterPattern_isOwnerWithTypedToken() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("/src/** @front");

    // Assert
    assertEquals(OWNER, ctx.segment());
    assertEquals("@front", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_secondOwnerInProgress_capturesCurrentTokenOnly() {
    // Arrange — caret is in a second owner; the first owner already separated by space.
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("/src/Foo.java @alice @bo");

    // Assert
    assertEquals(OWNER, ctx.segment());
    assertEquals("@bo", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_emailOwnerPrefix_isOwnerWithoutAtPrefix() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("/Foo.java john");

    // Assert
    assertEquals(OWNER, ctx.segment());
    assertEquals("john", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_commentLine_isComment() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("# top-level rule");

    // Assert
    assertEquals(COMMENT, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_commentAfterWhitespace_isComment() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("   # indented");

    // Assert
    assertEquals(COMMENT, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_partialSectionHeaderName_isSectionHeaderNameWithTypedName() {
    // Act — caret inside an unclosed [Backend.
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("[Backend");

    // Assert
    assertEquals(SECTION_HEADER_NAME, ctx.segment());
    assertEquals("Backend", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_optionalSectionHeaderPartialName_isSectionHeaderName() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("^[Back");

    // Assert
    assertEquals(SECTION_HEADER_NAME, ctx.segment());
    assertEquals("Back", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_emptySectionBrackets_isSectionHeaderNameWithEmptyText() {
    // Act — caret right after the opening '[' but before any character.
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("[");

    // Assert
    assertEquals(SECTION_HEADER_NAME, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_caretRightAfterSectionClose_isNone() {
    // Arrange — caret butted up against the ']' with no whitespace yet; no
    // useful completion exists there without breaking syntax.
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("[Backend]");

    // Assert
    assertEquals(NONE, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_sectionHeaderFollowedBySpace_isOwnerForDefaultOwners() {
    // Act — GitLab allows default owners on the section header line itself.
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("[Backend] ");

    // Assert
    assertEquals(OWNER, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_sectionHeaderAndApprovalCountAndOwner_isOwner() {
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("[Backend][2] @ba");

    // Assert
    assertEquals(OWNER, ctx.segment());
    assertEquals("@ba", ctx.typedSegmentText());
  }

  @Test
  public void fromLinePrefix_caretInsideApprovalCountBrackets_isNone() {
    // Arrange — inside [N] there's nothing useful to complete.
    // Act
    CodeownersCompletionContext ctx = CodeownersCompletionContext.fromLinePrefix("[Backend][");

    // Assert
    assertEquals(NONE, ctx.segment());
    assertEquals("", ctx.typedSegmentText());
  }
}
