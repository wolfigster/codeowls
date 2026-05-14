package net.wolfig.codeowls.settings;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import net.wolfig.codeowls.highlighting.CodeownersHighlightingColors;
import net.wolfig.codeowls.highlighting.CodeownersSyntaxHighlighter;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Provides the color-scheme panel at
 * <em>Settings → Editor → Color Scheme → CODEOWNERS</em>.
 */
public class CodeownersColorSettingsPage implements ColorSettingsPage {

  // The "Group//Leaf" syntax controls grouping in the settings tree.
  private static final AttributesDescriptor[] DESCRIPTORS = {
          new AttributesDescriptor("Comment", CodeownersHighlightingColors.COMMENT),
          new AttributesDescriptor("File pattern", CodeownersHighlightingColors.PATTERN),
          new AttributesDescriptor("Owner//User (@username)", CodeownersHighlightingColors.USER_OWNER),
          new AttributesDescriptor("Owner//Team (@org\\/team)", CodeownersHighlightingColors.TEAM_OWNER),
          new AttributesDescriptor("Owner//Role (@@maintainer)", CodeownersHighlightingColors.ROLE_OWNER),
          new AttributesDescriptor("Owner//Email address", CodeownersHighlightingColors.EMAIL_OWNER),
          new AttributesDescriptor("GitLab//Section header", CodeownersHighlightingColors.SECTION_HEADER),
          new AttributesDescriptor("GitLab//Approval count", CodeownersHighlightingColors.APPROVAL_COUNT),
          new AttributesDescriptor("Invalid token", CodeownersHighlightingColors.BAD_CHARACTER),
  };

  /**
   * Sample CODEOWNERS content shown in the preview pane.
   */
  private static final String DEMO_TEXT =
          """
                  # Global owner — matched when no other rule applies
                  * @global-owner1 @global-owner2
                  
                  # Later rules take precedence over earlier ones
                  *.java   @org/java-team
                  *.js     @org/js-team   js-lead@example.com
                  /build/logs/  @doctocat
                  docs/**/*.md  @org/docs  tech-writer@example.com
                  
                  # Negation pattern (GitLab) — exclude from a previous default
                  !/config/**/*.rb
                  
                  # GitLab section header with approval count AND default owners on the same line
                  [Backend][2] @org/backend  @alice
                  /backend/api/**  @@maintainer
                  
                  # GitLab optional section (approvals not required)
                  ^[Frontend][1] @org/frontend
                  /frontend/**  @bob  @carol
                  """;

  @Override
  public @NotNull String getDisplayName() {
    return CodeownersLanguage.ID;
  }

  @Override
  public @Nullable Icon getIcon() {
    return null;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new CodeownersSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return DEMO_TEXT;
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }
}
