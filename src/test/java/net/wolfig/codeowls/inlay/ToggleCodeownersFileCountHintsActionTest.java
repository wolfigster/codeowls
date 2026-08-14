package net.wolfig.codeowls.inlay;

import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Tests for {@link ToggleCodeownersFileCountHintsAction} — verifies it flips
 * the shared declarative-inlay provider setting, presents the right enable /
 * disable label, runs its {@code update} on a background thread, and keeps its
 * {@code PROVIDER_ID} in sync with {@code plugin.xml}.
 *
 * <p>The action wraps the application-level {@link DeclarativeInlayHintsSettings},
 * so a fixture is used to initialise the platform; the original setting value
 * is captured in {@link #setUp()} and restored in {@link #tearDown()} so the
 * global state isn't leaked between tests.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class ToggleCodeownersFileCountHintsActionTest {

  private CodeInsightTestFixture fixture;
  private DeclarativeInlayHintsSettings settings;
  private boolean originalEnabled;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    // The CodeInsight wrapper runs setUp/tearDown on the EDT, which the
    // fixture's editor-release checks require; the bare project fixture does not.
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
    settings = DeclarativeInlayHintsSettings.Companion.getInstance();
    originalEnabled = isEnabled();
  }

  @After
  public void tearDown() throws Exception {
    if (settings != null) {
      settings.setProviderEnabled(ToggleCodeownersFileCountHintsAction.PROVIDER_ID, originalEnabled);
    }
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  private boolean isEnabled() {
    return Boolean.TRUE.equals(
            settings.isProviderEnabled(ToggleCodeownersFileCountHintsAction.PROVIDER_ID));
  }

  @Test
  public void actionPerformed_flipsProviderEnabledState() {
    // Arrange — start from a known-enabled state.
    settings.setProviderEnabled(ToggleCodeownersFileCountHintsAction.PROVIDER_ID, true);
    ToggleCodeownersFileCountHintsAction action = new ToggleCodeownersFileCountHintsAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action);

    // Act / Assert — each invocation flips the persisted flag.
    action.actionPerformed(event);
    assertFalse("first toggle should disable", isEnabled());

    action.actionPerformed(event);
    assertTrue("second toggle should re-enable", isEnabled());
  }

  @Test
  public void update_whenEnabled_offersToDisable() {
    // Arrange
    settings.setProviderEnabled(ToggleCodeownersFileCountHintsAction.PROVIDER_ID, true);
    ToggleCodeownersFileCountHintsAction action = new ToggleCodeownersFileCountHintsAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action);

    // Act
    action.update(event);

    // Assert
    assertEquals("Codeowls: Disable file count hints", event.getPresentation().getText());
  }

  @Test
  public void update_whenDisabled_offersToEnable() {
    // Arrange
    settings.setProviderEnabled(ToggleCodeownersFileCountHintsAction.PROVIDER_ID, false);
    ToggleCodeownersFileCountHintsAction action = new ToggleCodeownersFileCountHintsAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action);

    // Act
    action.update(event);

    // Assert
    assertEquals("Codeowls: Enable file count hints", event.getPresentation().getText());
  }

  @Test
  public void getActionUpdateThread_isBackgroundThread() {
    // Act / Assert — update() only reads settings, so it is safe off the EDT.
    assertEquals(ActionUpdateThread.BGT,
            new ToggleCodeownersFileCountHintsAction().getActionUpdateThread());
  }

  @Test
  public void providerId_matchesPluginXmlDeclaration() throws Exception {
    // Arrange — the action's PROVIDER_ID must match the declarativeInlayProvider
    // registration, or the toggle would silently target a non-existent provider.
    String pluginXml;
    try (InputStream in = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
      assertNotNull("plugin.xml must be on the test classpath", in);
      pluginXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    // Act / Assert
    assertTrue("plugin.xml must declare providerId=\""
                    + ToggleCodeownersFileCountHintsAction.PROVIDER_ID + "\"",
            pluginXml.contains("providerId=\""
                    + ToggleCodeownersFileCountHintsAction.PROVIDER_ID + "\""));
  }
}
