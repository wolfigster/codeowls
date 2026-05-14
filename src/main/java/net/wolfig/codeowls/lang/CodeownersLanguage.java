package net.wolfig.codeowls.lang;

import com.intellij.lang.Language;

/**
 * Singleton language descriptor for CODEOWNERS files.
 *
 * <p>{@link #ID} is the single source of truth for the language identifier used
 * by {@code plugin.xml}, {@link com.intellij.openapi.editor.colors.TextAttributesKey}
 * external names, and the file-type display name.
 */
public final class CodeownersLanguage extends Language {

  /**
   * Language ID — must match the {@code language="..."} attribute in plugin.xml.
   */
  public static final String ID = "CODEOWNERS";

  public static final CodeownersLanguage INSTANCE = new CodeownersLanguage();

  private CodeownersLanguage() {
    super(ID);
  }
}
