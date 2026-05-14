package net.wolfig.codeowls.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * File type for CODEOWNERS files (GitHub, GitLab, Bitbucket).
 *
 * <p>Detection is done by filename pattern in {@code plugin.xml}
 * ({@code patterns="CODEOWNERS"}); there is no file extension to register.
 */
public final class CodeownersFileType extends LanguageFileType {

  public static final CodeownersFileType INSTANCE = new CodeownersFileType();

  private CodeownersFileType() {
    super(CodeownersLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return CodeownersLanguage.ID;
  }

  @Override
  public @NotNull String getDescription() {
    return "CODEOWNERS file";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "";
  }

  @Override
  public @Nullable Icon getIcon() {
    return null;
  }
}
