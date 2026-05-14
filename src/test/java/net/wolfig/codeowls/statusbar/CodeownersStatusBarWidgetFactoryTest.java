package net.wolfig.codeowls.statusbar;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersStatusBarWidgetFactory}.
 *
 * <p>The factory has very little behaviour of its own — only the static
 * pieces are unit-tested here. Widget construction is exercised by the
 * platform when the plugin is loaded; verifying it in a unit test would
 * require a full IDE fixture.
 *
 * <p>The factory id is persisted in the user's status-bar configuration, so
 * a "must equal the published constant" assertion guards against accidental
 * rename — the same way the {@code CODEOWNERS.*} {@code TextAttributesKey}
 * names are guarded elsewhere.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersStatusBarWidgetFactoryTest {

  @Test
  public void ID_default_isStablePublishedString() {
    // Act
    String id = CodeownersStatusBarWidgetFactory.ID;

    // Assert
    assertEquals("Codeowls.StatusBarWidget", id);
  }

  @Test
  public void getId_default_returnsIdConstant() {
    // Arrange
    CodeownersStatusBarWidgetFactory factory = new CodeownersStatusBarWidgetFactory();

    // Act
    String id = factory.getId();

    // Assert
    assertSame(CodeownersStatusBarWidgetFactory.ID, id);
  }

  @Test
  public void getDisplayName_default_returnsCodeownersString() {
    // Arrange
    CodeownersStatusBarWidgetFactory factory = new CodeownersStatusBarWidgetFactory();

    // Act
    String name = factory.getDisplayName();

    // Assert
    assertNotNull(name);
    assertEquals("CODEOWNERS", name);
  }
}
