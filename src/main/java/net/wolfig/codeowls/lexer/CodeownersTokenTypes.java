package net.wolfig.codeowls.lexer;

import com.intellij.psi.tree.IElementType;
import net.wolfig.codeowls.lang.CodeownersLanguage;

/**
 * {@link IElementType} constants for every token category produced by
 * {@link CodeownersLexer}.
 */
public final class CodeownersTokenTypes {

  /**
   * {@code # comment text}
   */
  public static final IElementType COMMENT =
          new IElementType("COMMENT", CodeownersLanguage.INSTANCE);

  /**
   * File-glob pattern that starts a rule line, e.g. {@code *.java} or {@code /src/**}.
   */
  public static final IElementType PATTERN =
          new IElementType("PATTERN", CodeownersLanguage.INSTANCE);

  /**
   * Individual GitHub/GitLab user owner, e.g. {@code @alice}.
   */
  public static final IElementType USER_OWNER =
          new IElementType("USER_OWNER", CodeownersLanguage.INSTANCE);

  /**
   * GitHub/GitLab team owner (contains a slash), e.g. {@code @org/backend}.
   */
  public static final IElementType TEAM_OWNER =
          new IElementType("TEAM_OWNER", CodeownersLanguage.INSTANCE);

  /**
   * GitLab role owner, e.g. {@code @@maintainer}, {@code @@developer}, {@code @@developers}.
   */
  public static final IElementType ROLE_OWNER =
          new IElementType("ROLE_OWNER", CodeownersLanguage.INSTANCE);

  /**
   * E-mail owner, e.g. {@code alice@example.com}.
   */
  public static final IElementType EMAIL_OWNER =
          new IElementType("EMAIL_OWNER", CodeownersLanguage.INSTANCE);

  /**
   * GitLab section header, e.g. {@code [Backend]} or {@code ^[Backend]} (optional).
   */
  public static final IElementType SECTION_HEADER =
          new IElementType("SECTION_HEADER", CodeownersLanguage.INSTANCE);

  /**
   * GitLab optional approval count that follows a section header, e.g. {@code [2]}.
   */
  public static final IElementType APPROVAL_COUNT =
          new IElementType("APPROVAL_COUNT", CodeownersLanguage.INSTANCE);

  /**
   * Token that does not fit any legal CODEOWNERS syntax.
   */
  public static final IElementType BAD_CHARACTER =
          new IElementType("BAD_CHARACTER", CodeownersLanguage.INSTANCE);

  private CodeownersTokenTypes() {
  }
}
