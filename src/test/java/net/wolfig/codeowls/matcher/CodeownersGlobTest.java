package net.wolfig.codeowls.matcher;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersGlob} — verifies the glob → regex translation
 * over the CODEOWNERS subset of gitignore syntax that the plugin advertises:
 * {@code *}, {@code **}, {@code ?}, leading {@code /}, trailing {@code /},
 * basename-anywhere matching, and escape sequences.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersGlobTest {

  private static boolean matches(String glob, String path) {
    return CodeownersGlob.compile(glob).matcher(path).matches();
  }

  // ---- basename anywhere (no slash, not rooted) ----

  @Test
  public void compile_noSlashPatternAgainstRootFile_matches() {
    // Arrange
    String glob = "*.java";
    String path = "Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_noSlashPatternAgainstSubdirectoryFile_matches() {
    // Arrange
    String glob = "*.java";
    String path = "src/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_noSlashPatternAgainstDeeplyNestedFile_matches() {
    // Arrange
    String glob = "*.java";
    String path = "src/main/java/net/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_noSlashPatternAgainstSuffixedFile_doesNotMatch() {
    // Arrange
    String glob = "*.java";
    String path = "Foo.java.bak";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  @Test
  public void compile_literalBasenameAtRoot_matches() {
    // Arrange
    String glob = "LICENSE";
    String path = "LICENSE";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_literalBasenameInSubdirectory_matches() {
    // Arrange
    String glob = "LICENSE";
    String path = "docs/LICENSE";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_literalBasenameAgainstSimilarName_doesNotMatch() {
    // Arrange
    String glob = "LICENSE";
    String path = "LICENSE.txt";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  // ---- rooted patterns (leading slash) ----

  @Test
  public void compile_rootedPatternAgainstRootFile_matches() {
    // Arrange
    String glob = "/CODEOWNERS";
    String path = "CODEOWNERS";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_rootedPatternAgainstSubdirectoryFile_doesNotMatch() {
    // Arrange
    String glob = "/CODEOWNERS";
    String path = "sub/CODEOWNERS";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  @Test
  public void compile_rootedPathSegmentAgainstExactPath_matches() {
    // Arrange
    String glob = "/src/main";
    String path = "src/main";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_rootedPathSegmentAgainstFileBelow_matches() {
    // Arrange
    String glob = "/src/main";
    String path = "src/main/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_rootedPathSegmentAtOtherDepth_doesNotMatch() {
    // Arrange
    String glob = "/src/main";
    String path = "other/src/main";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  // ---- patterns containing slash without leading slash are also anchored ----

  @Test
  public void compile_internalSlashPatternAgainstRootedFile_matches() {
    // Arrange
    String glob = "src/*.java";
    String path = "src/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_internalSlashPatternAgainstDeeperFile_doesNotMatch() {
    // Arrange
    String glob = "src/*.java";
    String path = "src/sub/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  @Test
  public void compile_internalSlashPatternAtOtherDepth_doesNotMatch() {
    // Arrange
    String glob = "src/*.java";
    String path = "other/src/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  // ---- single asterisk does not cross slashes ----

  @Test
  public void compile_singleStarWithinASegment_matches() {
    // Arrange
    String glob = "src/*.java";
    String path = "src/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_singleStarAcrossSlash_doesNotMatch() {
    // Arrange
    String glob = "src/*.java";
    String path = "src/a/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  // ---- double asterisk crosses path segments ----

  @Test
  public void compile_doubleStarAgainstZeroSegments_matches() {
    // Arrange
    String glob = "src/**/Foo.java";
    String path = "src/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_doubleStarAgainstSingleSegment_matches() {
    // Arrange
    String glob = "src/**/Foo.java";
    String path = "src/main/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_doubleStarAgainstMultipleSegments_matches() {
    // Arrange
    String glob = "src/**/Foo.java";
    String path = "src/main/java/net/Foo.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_doubleStarSuffixAgainstDirectChild_matches() {
    // Arrange
    String glob = "docs/**";
    String path = "docs/README.md";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_doubleStarSuffixAgainstNestedFile_matches() {
    // Arrange
    String glob = "docs/**";
    String path = "docs/a/b.md";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  // ---- single-char wildcard ----

  @Test
  public void compile_questionMarkAgainstOneChar_matches() {
    // Arrange
    String glob = "?.java";
    String path = "A.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_questionMarkAgainstTwoChars_doesNotMatch() {
    // Arrange
    String glob = "?.java";
    String path = "AB.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  @Test
  public void compile_questionMarkAgainstSlash_doesNotMatch() {
    // Arrange
    String glob = "?.java";
    String path = "/.java";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  // ---- trailing slash (directory-only patterns) ----

  @Test
  public void compile_trailingSlashAgainstDirectChild_matches() {
    // Arrange
    String glob = "docs/";
    String path = "docs/x.md";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_trailingSlashAgainstNestedFile_matches() {
    // Arrange
    String glob = "docs/";
    String path = "docs/sub/x.md";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_trailingSlashAgainstDirectoryEntry_doesNotMatch() {
    // Arrange — GitHub CODEOWNERS only owns files within the directory, not
    // the directory entry as a file path.
    String glob = "docs/";
    String path = "docs";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  // ---- implicit directory-match-everything-below ----

  @Test
  public void compile_directoryNamePatternAgainstDirectoryItself_matches() {
    // Arrange — a pattern that matches a directory implicitly matches its contents.
    String glob = "/docs";
    String path = "docs";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_directoryNamePatternAgainstContents_matches() {
    // Arrange
    String glob = "/docs";
    String path = "docs/x.md";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  // ---- regex specials escaped ----

  @Test
  public void compile_literalDotAgainstSameLiteral_matches() {
    // Arrange
    String glob = "a.b.c";
    String path = "a.b.c";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_literalDotAgainstArbitraryChars_doesNotMatch() {
    // Arrange
    String glob = "a.b.c";
    String path = "aXbXc";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  @Test
  public void compile_literalParens_matchAsRegularChars() {
    // Arrange
    String glob = "group(1)";
    String path = "group(1)";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_literalPlus_matchesAsRegularChar() {
    // Arrange
    String glob = "plus+";
    String path = "plus+";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  // ---- backslash escapes ----

  @Test
  public void compile_backslashEscapedStarAgainstStarFile_matches() {
    // Arrange — "\*.txt" should match a file literally named "*.txt".
    String glob = "\\*.txt";
    String path = "*.txt";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }

  @Test
  public void compile_backslashEscapedStarAgainstArbitraryFile_doesNotMatch() {
    // Arrange
    String glob = "\\*.txt";
    String path = "foo.txt";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertFalse(result);
  }

  // ---- negation prefix stripped (caller decides what to do) ----

  @Test
  public void compile_negationPrefix_isStrippedAndCompilesIdenticallyToPlain() {
    // Arrange
    String negatedGlob = "!*.java";
    String plainGlob = "*.java";

    // Act
    Pattern negated = CodeownersGlob.compile(negatedGlob);
    Pattern plain = CodeownersGlob.compile(plainGlob);

    // Assert
    assertEquals(plain.pattern(), negated.pattern());
  }

  // ---- empty / weird inputs ----

  @Test
  public void compile_emptyPatternAgainstEmptyPath_matches() {
    // Arrange — edge case: an empty glob compiles to a pattern that matches
    // an empty path without throwing.
    String glob = "";
    String path = "";

    // Act
    boolean result = matches(glob, path);

    // Assert
    assertTrue(result);
  }
}
