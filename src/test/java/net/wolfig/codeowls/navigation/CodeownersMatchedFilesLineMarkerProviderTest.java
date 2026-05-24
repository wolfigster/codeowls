package net.wolfig.codeowls.navigation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link CodeownersMatchedFilesLineMarkerProvider#matchingPaths} — the
 * pure selection of project paths a wildcard/directory rule matches (the data
 * behind the gutter "files matched by this rule" popup). The glob semantics
 * themselves are covered by {@code CodeownersGlobTest}; here we check the
 * filtering and the result cap.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersMatchedFilesLineMarkerProviderTest {

  @Test
  public void matchingPaths_extensionGlob_matchesBasenameAtAnyDepth() {
    // Arrange
    List<String> paths = List.of("Foo.java", "Bar.kt", "src/Baz.java");

    // Act
    List<String> matched = CodeownersMatchedFilesLineMarkerProvider.matchingPaths("*.java", paths);

    // Assert
    assertEquals(List.of("Foo.java", "src/Baz.java"), matched);
  }

  @Test
  public void matchingPaths_anchoredDoubleStar_matchesEverythingBeneath() {
    // Arrange
    List<String> paths = List.of("src/A.java", "src/sub/B.java", "docs/C.md");

    // Act
    List<String> matched = CodeownersMatchedFilesLineMarkerProvider.matchingPaths("/src/**", paths);

    // Assert
    assertEquals(List.of("src/A.java", "src/sub/B.java"), matched);
  }

  @Test
  public void matchingPaths_directoryPattern_matchesAllDescendants() {
    // Arrange
    List<String> paths = List.of("docs/x.md", "docs/y/z.md", "src/a.txt");

    // Act
    List<String> matched = CodeownersMatchedFilesLineMarkerProvider.matchingPaths("/docs/", paths);

    // Assert
    assertEquals(List.of("docs/x.md", "docs/y/z.md"), matched);
  }

  @Test
  public void matchingPaths_noMatches_returnsEmpty() {
    // Act
    List<String> matched = CodeownersMatchedFilesLineMarkerProvider.matchingPaths("*.kt", List.of("Foo.java"));

    // Assert
    assertEquals(List.of(), matched);
  }

  @Test
  public void matchingPaths_manyMatches_cappedAtMaxTargets() {
    // Arrange — more matching files than the cap.
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < CodeownersMatchedFilesLineMarkerProvider.MAX_TARGETS + 50; i++) {
      paths.add("src/File" + i + ".java");
    }

    // Act
    List<String> matched = CodeownersMatchedFilesLineMarkerProvider.matchingPaths("*.java", paths);

    // Assert
    assertEquals(CodeownersMatchedFilesLineMarkerProvider.MAX_TARGETS, matched.size());
  }
}
