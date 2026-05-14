package net.wolfig.codeowls.lang;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import org.junit.Test;

import javax.swing.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersFileType}.
 *
 * <p>Verifies the metadata the IDE exposes for CODEOWNERS files: name,
 * description, default extension (empty — files are detected by filename in
 * {@code plugin.xml}), absence of an icon, and the wired language instance.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersFileTypeTest {

  @Test
  public void INSTANCE_default_isNonNull() {
    // Act
    CodeownersFileType instance = CodeownersFileType.INSTANCE;

    // Assert
    assertNotNull(instance);
  }

  @Test
  public void getName_default_returnsLanguageId() {
    // Arrange
    CodeownersFileType fileType = CodeownersFileType.INSTANCE;

    // Act
    String name = fileType.getName();

    // Assert
    assertEquals(CodeownersLanguage.ID, name);
  }

  @Test
  public void getDescription_default_returnsHumanReadableString() {
    // Arrange
    CodeownersFileType fileType = CodeownersFileType.INSTANCE;

    // Act
    String description = fileType.getDescription();

    // Assert
    assertEquals("CODEOWNERS file", description);
  }

  @Test
  public void getDefaultExtension_default_returnsEmptyString() {
    // Arrange — files are detected by filename in plugin.xml; no extension.
    CodeownersFileType fileType = CodeownersFileType.INSTANCE;

    // Act
    String defaultExtension = fileType.getDefaultExtension();

    // Assert
    assertEquals("", defaultExtension);
  }

  @Test
  public void getIcon_default_returnsTextFileIcon() {
    // Arrange
    CodeownersFileType fileType = CodeownersFileType.INSTANCE;

    // Act
    Icon icon = fileType.getIcon();

    // Assert
    assertEquals(AllIcons.FileTypes.Text, icon);
  }

  @Test
  public void getLanguage_default_returnsCodeownersLanguageInstance() {
    // Arrange
    CodeownersFileType fileType = CodeownersFileType.INSTANCE;

    // Act
    Language language = fileType.getLanguage();

    // Assert
    assertSame(CodeownersLanguage.INSTANCE, language);
  }
}
