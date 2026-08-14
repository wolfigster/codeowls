package net.wolfig.codeowls.search;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import net.wolfig.codeowls.inlay.CodeownersMatchCounter;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.statusbar.CodeownersService;
import net.wolfig.codeowls.statusbar.EffectiveOwnership;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds project-content files effectively owned by an exact owner token.
 *
 * <p>Callers must invoke this under a background read action. Resolution is
 * delegated to {@link CodeownersService}, so last-match-wins, GitLab section
 * inheritance, nested CODEOWNERS files, and unsaved document changes all use
 * the same semantics as Explain Ownership and the status bar.
 */
@Service(Service.Level.PROJECT)
public final class OwnershipSearchService {

  private final Project project;

  public OwnershipSearchService(@NotNull Project project) {
    this.project = project;
  }

  public static @NotNull OwnershipSearchService getInstance(@NotNull Project project) {
    return project.getService(OwnershipSearchService.class);
  }

  private static @NotNull String relativePath(@NotNull VirtualFile root, @NotNull VirtualFile file) {
    String rootPath = root.getPath();
    String filePath = file.getPath();
    if (filePath.equals(rootPath)) return "";
    String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
    return filePath.startsWith(prefix) ? filePath.substring(prefix.length()) : filePath;
  }

  public @NotNull List<OwnedFile> findFilesOwnedBy(@NotNull String owner,
                                                   @NotNull VirtualFile codeownersFile,
                                                   @NotNull ProgressIndicator indicator) {
    VirtualFile root = CodeownersMatchCounter.projectRoot(codeownersFile);
    if (root == null || !root.isValid()) return List.of();

    CodeownersService resolver = CodeownersService.getInstance(project);
    List<OwnedFile> matches = new ArrayList<>();
    indicator.setIndeterminate(true);

    CodeownersMatchCounter.processProjectFiles(root, file -> {
      indicator.checkCanceled();
      if (!file.isValid()) return true;

      String relativePath = relativePath(root, file);
      indicator.setText2(relativePath);

      EffectiveOwnership ownership = resolver.resolveOwnership(file);
      if (codeownersFile.equals(ownership.codeownersFile()) && ownership.owners().contains(owner)) {
        CodeownersRule rule = ownership.rule();
        if (rule != null) matches.add(new OwnedFile(file, relativePath, rule));
      }
      return true;
    });

    matches.sort(Comparator.comparing(OwnedFile::relativePath));
    return List.copyOf(matches);
  }

  public record OwnedFile(@NotNull VirtualFile file,
                          @NotNull String relativePath,
                          @NotNull CodeownersRule effectiveRule) {
  }
}
