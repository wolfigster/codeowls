package net.wolfig.codeowls.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import net.wolfig.codeowls.inlay.CodeownersMatchCounter;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import net.wolfig.codeowls.matcher.CodeownersGlob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Adds a gutter icon to every wildcard or directory CODEOWNERS rule that lists
 * the project files the rule matches; clicking a file in the popup navigates to
 * it.
 *
 * <p>Only glob and directory patterns get a marker — an exact-path rule already
 * names its single file. The matched-file list is computed lazily (only when the
 * icon is clicked), so the daemon pass that places the marker stays cheap; the
 * click reuses {@link CodeownersMatchCounter}'s project walk and
 * {@link CodeownersGlob} matching. The list is capped at {@link #MAX_TARGETS}
 * to keep the popup responsive on broad rules like {@code /src/**}.
 */
public final class CodeownersMatchedFilesLineMarkerProvider extends RelatedItemLineMarkerProvider {

  static final int MAX_TARGETS = 1000;

  private static @NotNull Collection<? extends PsiElement> collectMatchedFiles(@NotNull PsiFile codeownersFile,
                                                                               @NotNull String pattern) {
    VirtualFile vf = codeownersFile.getVirtualFile();
    VirtualFile root = CodeownersMatchCounter.projectRoot(vf);
    if (root == null) return List.of();

    PsiManager psiManager = PsiManager.getInstance(codeownersFile.getProject());
    List<PsiElement> files = new ArrayList<>();
    for (String path : matchingPaths(pattern, CodeownersMatchCounter.collectProjectFilePaths(root))) {
      VirtualFile child = root.findFileByRelativePath(path);
      if (child == null || !child.isValid()) continue;
      PsiFile psi = psiManager.findFile(child);
      if (psi != null) files.add(psi);
    }
    return files;
  }

  /**
   * The (up to {@link #MAX_TARGETS}) project paths matched by {@code pattern}.
   * Pure, so the selection logic is unit-tested without the IntelliJ platform.
   */
  static @NotNull List<String> matchingPaths(@NotNull String pattern, @NotNull List<String> projectPaths) {
    Pattern compiled = CodeownersGlob.compile(pattern);
    List<String> matched = new ArrayList<>();
    for (String path : projectPaths) {
      if (compiled.matcher(path).matches()) {
        matched.add(path);
        if (matched.size() >= MAX_TARGETS) break;
      }
    }
    return matched;
  }

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element,
                                          @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    ASTNode node = element.getNode();
    if (node == null || node.getElementType() != CodeownersTokenTypes.PATTERN) return;

    String pattern = element.getText();
    if (pattern == null || !CodeownersMatchCounter.isGlobOrDirectoryPattern(pattern)) return;

    PsiFile codeownersFile = element.getContainingFile();
    if (codeownersFile == null) return;

    NotNullLazyValue<Collection<? extends PsiElement>> targets =
            NotNullLazyValue.lazy(() -> ReadAction.compute(() -> collectMatchedFiles(codeownersFile, pattern)));

    result.add(NavigationGutterIconBuilder.create(AllIcons.Nodes.Folder)
            .setTargets(targets)
            .setTooltipText("Files matched by this CODEOWNERS rule")
            .setPopupTitle("Files Matched by " + pattern)
            .setEmptyPopupText("No files match this rule")
            .createLineMarkerInfo(element));
  }

  @Override
  public @NotNull String getName() {
    return "CODEOWNERS rule matched files";
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Nodes.Folder;
  }
}
