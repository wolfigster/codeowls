package net.wolfig.codeowls.highlighting;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import net.wolfig.codeowls.lang.CodeownersLanguage;

/**
 * Editor {@link TextAttributesKey}s for every CODEOWNERS token category.
 *
 * <p>The external names ({@code CODEOWNERS.*}) are the persistence keys that
 * the IDE writes to user color schemes — they must remain stable across plugin
 * versions. The defaults fall back to standard IDE scheme keys so highlighting
 * looks reasonable in both light and dark themes out of the box.
 */
public final class CodeownersHighlightingColors {

  private static final String PREFIX = CodeownersLanguage.ID + ".";

  public static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey(
          PREFIX + "COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

  public static final TextAttributesKey PATTERN = TextAttributesKey.createTextAttributesKey(
          PREFIX + "PATTERN", DefaultLanguageHighlighterColors.CLASS_NAME);

  /**
   * Single-user owner: {@code @alice}.
   */
  public static final TextAttributesKey USER_OWNER = TextAttributesKey.createTextAttributesKey(
          PREFIX + "USER_OWNER", DefaultLanguageHighlighterColors.INSTANCE_METHOD);

  /**
   * Team owner: {@code @org/team}.
   */
  public static final TextAttributesKey TEAM_OWNER = TextAttributesKey.createTextAttributesKey(
          PREFIX + "TEAM_OWNER", DefaultLanguageHighlighterColors.NUMBER);

  /**
   * Role owner (GitLab): {@code @@maintainer}, {@code @@developer}, {@code @@developers}.
   */
  public static final TextAttributesKey ROLE_OWNER = TextAttributesKey.createTextAttributesKey(
          PREFIX + "ROLE_OWNER", DefaultLanguageHighlighterColors.STRING);

  /**
   * E-mail owner: {@code alice@example.com}.
   */
  public static final TextAttributesKey EMAIL_OWNER = TextAttributesKey.createTextAttributesKey(
          PREFIX + "EMAIL_OWNER", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

  /**
   * GitLab {@code [Section Name]} header.
   */
  public static final TextAttributesKey SECTION_HEADER = TextAttributesKey.createTextAttributesKey(
          PREFIX + "SECTION_HEADER", DefaultLanguageHighlighterColors.KEYWORD);

  /**
   * GitLab optional approval count {@code [2]} that follows a section header.
   */
  public static final TextAttributesKey APPROVAL_COUNT = TextAttributesKey.createTextAttributesKey(
          PREFIX + "APPROVAL_COUNT", DefaultLanguageHighlighterColors.METADATA);

  /**
   * Tokens that do not match any valid CODEOWNERS syntax.
   */
  public static final TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(
          PREFIX + "BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

  private CodeownersHighlightingColors() {
  }
}
