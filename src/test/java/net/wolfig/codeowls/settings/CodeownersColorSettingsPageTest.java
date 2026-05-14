package net.wolfig.codeowls.settings;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import net.wolfig.codeowls.highlighting.CodeownersHighlightingColors;
import net.wolfig.codeowls.highlighting.CodeownersSyntaxHighlighter;
import org.junit.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersColorSettingsPage}.
 *
 * <p>Covers the metadata surfaced by the
 * <em>Settings → Editor → Color Scheme → CODEOWNERS</em> page: display name,
 * absent icon, highlighter wiring, the demo-text preview, and the set of
 * {@code AttributesDescriptor}s. The descriptor set is compared against
 * {@link CodeownersHighlightingColors} so that adding a new highlighted token
 * category without exposing it in the settings page fails this test.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersColorSettingsPageTest {

  @Test
  public void displayName_isLanguageId() {
    // Arrange
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();

    // Act
    String displayName = page.getDisplayName();

    // Assert
    assertEquals("CODEOWNERS", displayName);
  }

  @Test
  public void icon_isNull() {
    // Arrange
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();

    // Act
    Icon icon = page.getIcon();

    // Assert
    assertNull(icon);
  }

  @Test
  public void highlighter_isCodeownersSyntaxHighlighter() {
    // Arrange
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();

    // Act
    SyntaxHighlighter highlighter = page.getHighlighter();

    // Assert
    assertNotNull(highlighter);
    assertTrue(highlighter instanceof CodeownersSyntaxHighlighter);
  }

  @Test
  public void demoText_isNonEmpty() {
    // Arrange
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();

    // Act
    String demo = page.getDemoText();

    // Assert
    assertNotNull(demo);
    assertFalse(demo.isBlank());
  }

  @Test
  public void demoText_coversTheInterestingTokenKinds() {
    // Arrange
    // The preview pane is the user's first impression — every documented token
    // category should appear so users can see what each setting affects.
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();

    // Act
    String demo = page.getDemoText();

    // Assert
    assertTrue("comment", demo.contains("#"));
    assertTrue("user owner", demo.contains("@global-owner1") || demo.contains("@alice"));
    assertTrue("team owner", demo.contains("@org/"));
    assertTrue("role owner", demo.contains("@@"));
    assertTrue("email owner", demo.contains("@example.com"));
    assertTrue("section header", demo.contains("[Backend]"));
    assertTrue("optional section", demo.contains("^[Frontend]"));
    assertTrue("approval count", demo.contains("[2]"));
    assertTrue("negation pattern", demo.contains("!/"));
  }

  @Test
  public void attributeDescriptors_coverEveryHighlightedTokenKind() {
    // Arrange
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();
    Set<String> expected = new HashSet<>(Arrays.asList(
            CodeownersHighlightingColors.COMMENT.getExternalName(),
            CodeownersHighlightingColors.PATTERN.getExternalName(),
            CodeownersHighlightingColors.USER_OWNER.getExternalName(),
            CodeownersHighlightingColors.TEAM_OWNER.getExternalName(),
            CodeownersHighlightingColors.ROLE_OWNER.getExternalName(),
            CodeownersHighlightingColors.EMAIL_OWNER.getExternalName(),
            CodeownersHighlightingColors.SECTION_HEADER.getExternalName(),
            CodeownersHighlightingColors.APPROVAL_COUNT.getExternalName(),
            CodeownersHighlightingColors.BAD_CHARACTER.getExternalName()));

    // Act
    AttributesDescriptor[] descriptors = page.getAttributeDescriptors();
    Set<String> actualKeys = new HashSet<>();
    for (AttributesDescriptor d : descriptors) {
      actualKeys.add(d.getKey().getExternalName());
    }

    // Assert
    assertEquals(expected, actualKeys);
  }

  @Test
  public void colorDescriptors_isEmpty() {
    // Arrange
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();

    // Act
    ColorDescriptor[] descriptors = page.getColorDescriptors();

    // Assert
    assertNotNull(descriptors);
    assertEquals(0, descriptors.length);
  }

  @Test
  public void additionalHighlightingTagToDescriptorMap_isNull() {
    // Arrange
    CodeownersColorSettingsPage page = new CodeownersColorSettingsPage();

    // Act
    var map = page.getAdditionalHighlightingTagToDescriptorMap();

    // Assert
    assertNull(map);
  }
}
