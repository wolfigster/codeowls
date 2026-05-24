package net.wolfig.codeowls.refactoring;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link CodeownersPathRewriter} — the pure pattern-rewrite applied
 * when a file or directory is moved or renamed.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersPathRewriterTest {

  // -- file moves / renames -----------------------------------------------

  @Test
  public void rewritePattern_anchoredExactFile_isRewritten() {
    assertEquals("/src/new/Foo.java",
            CodeownersPathRewriter.rewritePattern("/src/old/Foo.java", "src/old/Foo.java", "src/new/Foo.java"));
  }

  @Test
  public void rewritePattern_unanchoredExactFile_keepsUnanchored() {
    assertEquals("src/new/Foo.java",
            CodeownersPathRewriter.rewritePattern("src/old/Foo.java", "src/old/Foo.java", "src/new/Foo.java"));
  }

  // -- directory moves / renames ------------------------------------------

  @Test
  public void rewritePattern_directorySlashForm_isRewritten() {
    assertEquals("/src/new/",
            CodeownersPathRewriter.rewritePattern("/src/old/", "src/old", "src/new"));
  }

  @Test
  public void rewritePattern_directoryDoubleStarForm_isRewritten() {
    assertEquals("/src/new/**",
            CodeownersPathRewriter.rewritePattern("/src/old/**", "src/old", "src/new"));
  }

  @Test
  public void rewritePattern_fileNestedUnderMovedDirectory_isRewritten() {
    assertEquals("/src/new/sub/Bar.java",
            CodeownersPathRewriter.rewritePattern("/src/old/sub/Bar.java", "src/old", "src/new"));
  }

  @Test
  public void rewritePattern_globNestedUnderMovedDirectory_isRewritten() {
    assertEquals("/src/new/*.java",
            CodeownersPathRewriter.rewritePattern("/src/old/*.java", "src/old", "src/new"));
  }

  @Test
  public void rewritePattern_exactDirectoryWithoutTrailingSlash_isRewritten() {
    assertEquals("/src/new",
            CodeownersPathRewriter.rewritePattern("/src/old", "src/old", "src/new"));
  }

  // -- negation preserved --------------------------------------------------

  @Test
  public void rewritePattern_negatedPattern_preservesBang() {
    assertEquals("!/src/new/**",
            CodeownersPathRewriter.rewritePattern("!/src/old/**", "src/old", "src/new"));
  }

  // -- patterns that must NOT change --------------------------------------

  @Test
  public void rewritePattern_basenameGlob_isNotRewritten() {
    assertNull(CodeownersPathRewriter.rewritePattern("*.java", "src/old/Foo.java", "src/new/Foo.java"));
  }

  @Test
  public void rewritePattern_unrelatedPath_isNotRewritten() {
    assertNull(CodeownersPathRewriter.rewritePattern("/docs/guide.md", "src/old", "src/new"));
  }

  @Test
  public void rewritePattern_partialSegmentPrefix_isNotRewritten() {
    // "src/older" must not be treated as living under "src/old".
    assertNull(CodeownersPathRewriter.rewritePattern("/src/older/X.java", "src/old", "src/new"));
  }
}
