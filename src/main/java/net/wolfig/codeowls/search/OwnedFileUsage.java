package net.wolfig.codeowls.search;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.rules.UsageInFile;
import net.wolfig.codeowls.search.OwnershipSearchService.OwnedFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * A navigable Usage View entry for an effectively owned file.
 */
final class OwnedFileUsage implements UsageInFile {

  private final Project project;
  private final OwnedFile ownedFile;
  private final UsagePresentation presentation;

  OwnedFileUsage(@NotNull Project project, @NotNull OwnedFile ownedFile) {
    this.project = project;
    this.ownedFile = ownedFile;
    this.presentation = new UsagePresentation() {
      @Override
      public @Nullable Icon getIcon() {
        return ownedFile.file().getFileType().getIcon();
      }

      @Override
      public TextChunk @NotNull [] getText() {
        return new TextChunk[]{new TextChunk(new TextAttributes(), ownedFile.relativePath())};
      }

      @Override
      public @NotNull String getPlainText() {
        return ownedFile.relativePath();
      }

      @Override
      public @NotNull String getTooltipText() {
        return ownedFile.relativePath() + " — " + ownedFile.effectiveRule().pattern()
                + " " + String.join(" ", ownedFile.effectiveRule().owners());
      }
    };
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return ownedFile.file();
  }

  @Override
  public @NotNull UsagePresentation getPresentation() {
    return presentation;
  }

  @Override
  public boolean isValid() {
    return !project.isDisposed() && ownedFile.file().isValid();
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public @Nullable FileEditorLocation getLocation() {
    return null;
  }

  @Override
  public void selectInEditor() {
    navigate(false);
  }

  @Override
  public void highlightInEditor() {
    navigate(false);
  }

  @Override
  public void navigate(boolean requestFocus) {
    new OpenFileDescriptor(project, ownedFile.file()).navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return isValid();
  }

  @Override
  public boolean equals(Object other) {
    return this == other
            || other instanceof OwnedFileUsage usage && ownedFile.file().equals(usage.ownedFile.file());
  }

  @Override
  public int hashCode() {
    return Objects.hash(ownedFile.file());
  }
}
