package net.wolfig.codeowls.inlay;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pure-logic tests for {@link CodeownersMatchCounter} — exercise the
 * single-file-pattern filter, the path-matching loop, and the wording helper
 * in {@link CodeownersFileCountInlayHintsProvider} without needing the
 * declarative-inlay test harness.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersMatchCounterTest {

  // -- isGlobOrDirectoryPattern -------------------------------------------

  @Test
  public void isGlobOrDirectoryPattern_anchoredFilePath_returnsFalse() {
    // Arrange — pinned-down file paths trivially match a single file, so no
    // hint is emitted.

    // Act
    boolean show = CodeownersMatchCounter.isGlobOrDirectoryPattern("/src/main/java/Foo.java");

    // Assert
    assertFalse(show);
  }

  @Test
  public void isGlobOrDirectoryPattern_basenameOnly_returnsFalse() {
    // Arrange — "Foo.java" almost always resolves to a single file in
    // practice; skip the hint to avoid "// 1 file" noise.

    // Act
    boolean show = CodeownersMatchCounter.isGlobOrDirectoryPattern("Foo.java");

    // Assert
    assertFalse(show);
  }

  @Test
  public void isGlobOrDirectoryPattern_starGlob_returnsTrue() {
    assertTrue(CodeownersMatchCounter.isGlobOrDirectoryPattern("*.java"));
  }

  @Test
  public void isGlobOrDirectoryPattern_doubleStar_returnsTrue() {
    assertTrue(CodeownersMatchCounter.isGlobOrDirectoryPattern("/src/**"));
  }

  @Test
  public void isGlobOrDirectoryPattern_directoryPattern_returnsTrue() {
    assertTrue(CodeownersMatchCounter.isGlobOrDirectoryPattern("/docs/"));
  }

  @Test
  public void isGlobOrDirectoryPattern_questionMarkGlob_returnsTrue() {
    assertTrue(CodeownersMatchCounter.isGlobOrDirectoryPattern("/src/Foo?.java"));
  }

  @Test
  public void isGlobOrDirectoryPattern_characterClassGlob_returnsTrue() {
    assertTrue(CodeownersMatchCounter.isGlobOrDirectoryPattern("/src/[ab]/Foo.java"));
  }

  @Test
  public void isGlobOrDirectoryPattern_empty_returnsFalse() {
    assertFalse(CodeownersMatchCounter.isGlobOrDirectoryPattern(""));
  }

  // -- computeCountsByLine -------------------------------------------------

  @Test
  public void computeCountsByLine_globRule_countsMatchingFiles() {
    // Arrange
    CharSequence content = "*.java @backend\n";
    List<String> projectFiles = List.of(
            "src/Foo.java",
            "src/Bar.java",
            "src/test/Baz.java",
            "src/notes.md");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert
    assertEquals(Map.of(0, 3), counts);
  }

  @Test
  public void computeCountsByLine_directoryPattern_countsAllDescendants() {
    // Arrange
    CharSequence content = "/src/** @backend\n";
    List<String> projectFiles = List.of(
            "src/main/Foo.java",
            "src/test/Bar.java",
            "docs/README.md");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert
    assertEquals(Map.of(0, 2), counts);
  }

  @Test
  public void computeCountsByLine_singleFileRule_isNotIncluded() {
    // Arrange — exact-path rules don't get an inlay hint.
    CharSequence content = "/src/Foo.java @owner\n*.md @docs\n";
    List<String> projectFiles = List.of("src/Foo.java", "docs/README.md");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert — only the glob line (index 1) is reported.
    assertEquals(Map.of(1, 1), counts);
    assertFalse(counts.containsKey(0));
  }

  @Test
  public void computeCountsByLine_basenameOnlyRule_isNotIncluded() {
    // Arrange — a bare basename like "Makefile" is excluded for the same
    // reason: in practice it resolves to a single file and "// 1 file" hints
    // are noise. Only globs and directory patterns should produce hints.
    CharSequence content = "Makefile @build\n*.java @backend\n";
    List<String> projectFiles = List.of("Makefile", "src/Foo.java");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert — only the *.java glob line (index 1) is reported.
    assertEquals(Map.of(1, 1), counts);
    assertFalse("Makefile line should be skipped: " + counts, counts.containsKey(0));
  }

  @Test
  public void computeCountsByLine_directoryRule_isIncluded() {
    // Arrange — a directory pattern is exactly the kind of rule the hint is
    // designed for: the count tells the reader how big the directory is.
    CharSequence content = "/docs/ @writers\n";
    List<String> projectFiles = List.of(
            "docs/README.md", "docs/setup.md", "docs/tutorial/intro.md", "src/Foo.java");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert
    assertEquals(Map.of(0, 3), counts);
  }

  @Test
  public void computeCountsByLine_emptyText_returnsEmptyMap() {
    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            "", null, List.of("foo"));

    // Assert
    assertTrue(counts.isEmpty());
  }

  @Test
  public void computeCountsByLine_commentsAndBlankLines_areSkipped() {
    // Arrange — only the glob rule should produce a hint entry.
    CharSequence content =
            """
                    # top-level rules
                    
                    *.java @backend
                    # another comment
                    """;
    List<String> projectFiles = List.of("Foo.java", "Bar.java");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert — the rule is on line 2 (0-based) and has 2 matches.
    assertEquals(Map.of(2, 2), counts);
  }

  @Test
  public void computeCountsByLine_sectionHeader_isSkipped() {
    // Arrange — section header lines should not be counted as rules.
    CharSequence content =
            """
                    [Backend]
                    *.java @backend
                    """;
    List<String> projectFiles = List.of("Foo.java");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert
    assertEquals(Map.of(1, 1), counts);
    assertFalse("section header should not appear: " + counts, counts.containsKey(0));
  }

  @Test
  public void computeCountsByLine_globMatchingZeroFiles_stillReportsLineWithZero() {
    // Arrange — a rule that matches nothing still gets a "// 0 files" hint
    // so the user can see the rule is currently dead.
    CharSequence content = "*.kt @kotlin\n";
    List<String> projectFiles = List.of("Foo.java");

    // Act
    Map<Integer, Integer> counts = CodeownersMatchCounter.computeCountsByLine(
            content, null, projectFiles);

    // Assert
    assertEquals(Map.of(0, 0), counts);
  }

  // -- inlay text formatting ----------------------------------------------

  @Test
  public void formatHintText_oneFile_returnsSingularNoun() {
    assertEquals("1 file",
            CodeownersFileCountInlayHintsProvider.formatHintText(1));
  }

  @Test
  public void formatHintText_zeroFiles_returnsPluralNoun() {
    assertEquals("0 files",
            CodeownersFileCountInlayHintsProvider.formatHintText(0));
  }

  @Test
  public void formatHintText_manyFiles_returnsPluralNoun() {
    assertEquals("14 files",
            CodeownersFileCountInlayHintsProvider.formatHintText(14));
  }

  // -- pattern-end offset (where the inlay anchors) ----------------------

  @Test
  public void patternEndOffsetInLine_plainPattern_returnsLengthOfPattern() {
    // Arrange — only the pattern, no owners yet.
    String line = "*.ts";

    // Act
    int end = CodeownersFileCountInlayHintsProvider.patternEndOffsetInLine(line);

    // Assert
    assertEquals(4, end);
  }

  @Test
  public void patternEndOffsetInLine_patternFollowedByOwner_returnsOffsetAtFirstSpace() {
    // Arrange — the inlay should anchor between pattern and owner.
    String line = "*.ts @frontend-team";

    // Act
    int end = CodeownersFileCountInlayHintsProvider.patternEndOffsetInLine(line);

    // Assert — offset of the space (4) is also the position before "@frontend".
    assertEquals(4, end);
  }

  @Test
  public void patternEndOffsetInLine_leadingWhitespace_skipsWhitespaceBeforePattern() {
    // Arrange
    String line = "  /src/** @backend";

    // Act
    int end = CodeownersFileCountInlayHintsProvider.patternEndOffsetInLine(line);

    // Assert — pattern "/src/**" runs from offset 2 to offset 9.
    assertEquals(9, end);
  }

  @Test
  public void patternEndOffsetInLine_blankLine_returnsZero() {
    // Arrange — a line with no pattern produces no anchor.
    String line = "   ";

    // Act
    int end = CodeownersFileCountInlayHintsProvider.patternEndOffsetInLine(line);

    // Assert
    assertEquals(0, end);
  }

  @Test
  public void patternEndOffsetInLine_emptyLine_returnsZero() {
    assertEquals(0, CodeownersFileCountInlayHintsProvider.patternEndOffsetInLine(""));
  }
}
