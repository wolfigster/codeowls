package net.wolfig.codeowls.statusbar;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import net.wolfig.codeowls.matcher.CodeownersGlob;
import net.wolfig.codeowls.matcher.CodeownersRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link CodeownersStatusBarWidget} — covers the
 * {@link com.intellij.openapi.wm.StatusBarWidget.TextPresentation} surface:
 * widget id, alignment, presentation self-binding, plus the text and tooltip
 * formatting for empty, single-rule, many-owner, and HTML-unsafe inputs.
 *
 * <p>The widget exposes its formatting, which read a private {@code resolution} field
 * written by an async background read action. The async pipeline isn't worth
 * replicating in unit tests, so the formatting cases use reflection to seed
 * {@code resolution} with a deterministic value — that's still a behavioral
 * check of the observable {@code TextPresentation} output, just with a
 * controlled input instead of one parsed out of an in-memory CODEOWNERS file.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersStatusBarWidgetTest {

  private CodeInsightTestFixture fixture;

  private static CodeownersOwnerResolution resolution(String pattern, List<String> owners) {
    CodeownersRule rule = new CodeownersRule(
            pattern, owners, CodeownersGlob.compile(pattern), null, 0);
    return new CodeownersOwnerResolution(rule);
  }

  /**
   * Reflection-based seam for tests; the field is volatile and otherwise EDT-driven.
   */
  private static void setResolution(CodeownersStatusBarWidget widget,
                                    CodeownersOwnerResolution res) {
    try {
      Field f = CodeownersStatusBarWidget.class.getDeclaredField("resolution");
      f.setAccessible(true);
      f.set(widget, res);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  private CodeownersStatusBarWidget widget() {
    return new CodeownersStatusBarWidget(fixture.getProject());
  }

  // -- presentation metadata -----------------------------------------------

  @Test
  public void ID_default_returnsFactoryIdConstant() {
    // Act
    String id = widget().ID();

    // Assert — published widget id, must not change between versions.
    assertEquals("Codeowls.StatusBarWidget", id);
    assertEquals(CodeownersStatusBarWidgetFactory.ID, id);
  }

  @Test
  public void getPresentation_default_returnsTheWidgetItself() {
    // Arrange
    CodeownersStatusBarWidget w = widget();

    // Act
    Object presentation = w.getPresentation();

    // Assert — widget implements TextPresentation; getPresentation should return `this`.
    assertSame(w, presentation);
  }

  @Test
  public void getAlignment_default_returnsLeftAlignment() {
    // Act
    float alignment = widget().getAlignment();

    // Assert
    assertEquals(Component.LEFT_ALIGNMENT, alignment, 0f);
  }

  @Test
  public void getClickConsumer_default_returnsNonNullConsumer() {
    // Act
    Object consumer = widget().getClickConsumer();

    // Assert
    assertNotNull(consumer);
  }

  // -- getText -------------------------------------------------------------

  @Test
  public void getText_noResolution_returnsNoCodeownersFallback() {
    // Act
    String text = widget().getText();

    // Assert — initial state shown when no rule matches the current file.
    assertEquals("No CODEOWNERS", text);
  }

  @Test
  public void getText_singleOwner_returnsGlyphPrefixedOwner() {
    // Arrange
    CodeownersStatusBarWidget w = widget();
    setResolution(w, resolution("*.java", List.of("@alice")));

    // Act
    String text = w.getText();

    // Assert
    assertEquals("👥 @alice", text);
  }

  @Test
  public void getText_threeOwners_returnsAllSeparatedBySpace() {
    // Arrange — at the visible cap; no overflow suffix.
    CodeownersStatusBarWidget w = widget();
    setResolution(w, resolution("*.java", List.of("@alice", "@bob", "@carol")));

    // Act
    String text = w.getText();

    // Assert
    assertEquals("👥 @alice @bob @carol", text);
  }

  @Test
  public void getText_moreThanThreeOwners_returnsFirstThreeAndOverflowCount() {
    // Arrange
    CodeownersStatusBarWidget w = widget();
    setResolution(w, resolution("*.java",
            List.of("@a", "@b", "@c", "@d", "@e")));

    // Act
    String text = w.getText();

    // Assert
    assertEquals("👥 @a @b @c +2", text);
  }

  // -- getTooltipText ------------------------------------------------------

  @Test
  public void getTooltipText_noResolution_returnsNoMatchMessage() {
    // Act
    String tooltip = widget().getTooltipText();

    // Assert
    assertEquals("No CODEOWNERS rule matches this file", tooltip);
  }

  @Test
  public void getTooltipText_resolutionWithRule_returnsHtmlWithOwnersPatternAndSource() {
    // Arrange
    CodeownersStatusBarWidget w = widget();
    setResolution(w, resolution("*.java", List.of("@alice", "@bob")));

    // Act
    String tooltip = w.getTooltipText();

    // Assert
    assertTrue("expected HTML wrapper, got: " + tooltip, tooltip.startsWith("<html>"));
    assertTrue("expected HTML close, got: " + tooltip, tooltip.endsWith("</html>"));
    assertTrue("owners section: " + tooltip, tooltip.contains("@alice, @bob"));
    assertTrue("pattern section: " + tooltip, tooltip.contains("*.java"));
    // No source file on synthetic rules — the widget falls back to "(unknown)".
    assertTrue("source section: " + tooltip, tooltip.contains("(unknown)"));
  }

  @Test
  public void getTooltipText_patternContainingAngleBrackets_escapesXmlEntities() {
    // Arrange — guarantee tooltip HTML can't be hijacked by a pattern that
    // happens to contain XML-special characters.
    CodeownersStatusBarWidget w = widget();
    setResolution(w, resolution("<foo>.txt", List.of("@team")));

    // Act
    String tooltip = w.getTooltipText();

    // Assert
    assertTrue("expected escaped pattern, got: " + tooltip, tooltip.contains("&lt;foo&gt;.txt"));
    assertFalse("raw pattern must not leak: " + tooltip, tooltip.contains("<foo>.txt"));
  }

  @Test
  public void getTooltipText_ownerContainingAmpersand_escapesXmlEntities() {
    // Arrange
    CodeownersStatusBarWidget w = widget();
    setResolution(w, resolution("*.md", List.of("@a&b-team")));

    // Act
    String tooltip = w.getTooltipText();

    // Assert
    assertTrue("expected escaped ampersand, got: " + tooltip, tooltip.contains("@a&amp;b-team"));
  }
}
