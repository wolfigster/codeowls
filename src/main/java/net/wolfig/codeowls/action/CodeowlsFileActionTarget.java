package net.wolfig.codeowls.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the single real file targeted by a Codeowls context action.
 */
public final class CodeowlsFileActionTarget {

  private CodeowlsFileActionTarget() {
  }

  public static @Nullable VirtualFile from(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null || project.isDisposed()) return null;

    VirtualFile[] selected = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (selected != null && selected.length > 1) return null;

    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null || !file.isValid() || file.isDirectory()
            || "CODEOWNERS".equals(file.getName())) {
      return null;
    }
    return file;
  }

  /**
   * Like {@link #from(AnActionEvent)}, but excludes files outside the project.
   */
  public static @Nullable VirtualFile projectFileFrom(@NotNull AnActionEvent event) {
    VirtualFile file = from(event);
    Project project = event.getProject();
    return file != null && project != null
            && ProjectFileIndex.getInstance(project).isInProject(file)
            ? file
            : null;
  }
}
