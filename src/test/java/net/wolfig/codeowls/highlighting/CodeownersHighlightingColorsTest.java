package net.wolfig.codeowls.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link CodeownersHighlightingColors}.
 *
 * <p>Pins each {@link TextAttributesKey} external name used by the plugin.
 * These strings are persisted into user color-scheme XML files; renaming them
 * silently resets any custom colors a user has chosen, so this test exists to
 * make accidental renames loud.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersHighlightingColorsTest {

  @Test
  public void getExternalName_commentKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.COMMENT;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.COMMENT", externalName);
  }

  @Test
  public void getExternalName_patternKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.PATTERN;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.PATTERN", externalName);
  }

  @Test
  public void getExternalName_userOwnerKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.USER_OWNER;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.USER_OWNER", externalName);
  }

  @Test
  public void getExternalName_teamOwnerKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.TEAM_OWNER;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.TEAM_OWNER", externalName);
  }

  @Test
  public void getExternalName_roleOwnerKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.ROLE_OWNER;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.ROLE_OWNER", externalName);
  }

  @Test
  public void getExternalName_emailOwnerKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.EMAIL_OWNER;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.EMAIL_OWNER", externalName);
  }

  @Test
  public void getExternalName_sectionHeaderKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.SECTION_HEADER;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.SECTION_HEADER", externalName);
  }

  @Test
  public void getExternalName_approvalCountKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.APPROVAL_COUNT;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.APPROVAL_COUNT", externalName);
  }

  @Test
  public void getExternalName_badCharacterKey_returnsPublishedConstant() {
    // Arrange
    TextAttributesKey key = CodeownersHighlightingColors.BAD_CHARACTER;

    // Act
    String externalName = key.getExternalName();

    // Assert
    assertNotNull(key);
    assertEquals("CODEOWNERS.BAD_CHARACTER", externalName);
  }
}
