package net.wolfig.codeowls.lang;

import com.intellij.lang.Language;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersLanguage}.
 *
 * <p>Locks down the language identifier and the singleton contract. The ID is a
 * load-bearing constant — it is referenced from {@code plugin.xml} and used as
 * the prefix for every persisted {@code TextAttributesKey} external name, so a
 * silent rename would invalidate users' saved color customizations.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersLanguageTest {

  @Test
  public void ID_default_isStablePublishedString() {
    // Act
    String id = CodeownersLanguage.ID;

    // Assert
    assertEquals("CODEOWNERS", id);
  }

  @Test
  public void INSTANCE_default_isSingleton() {
    // Act
    CodeownersLanguage first = CodeownersLanguage.INSTANCE;
    CodeownersLanguage second = CodeownersLanguage.INSTANCE;

    // Assert
    assertNotNull(first);
    assertSame(first, second);
  }

  @Test
  public void getID_instance_returnsIdConstant() {
    // Arrange
    Language language = CodeownersLanguage.INSTANCE;

    // Act
    String id = language.getID();

    // Assert
    assertEquals(CodeownersLanguage.ID, id);
  }
}
