package net.wolfig.codeowls.statusbar;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Project-level service that locates the active CODEOWNERS file, parses it
 * into rules (cached by modification stamp), and resolves the matching rule
 * for a given file using last-match-wins semantics.
 *
 * <p>The service is intentionally listener-free: it reparses lazily when a
 * caller asks for rules and the cached document/file stamp has changed. The
 * widget owns its own VFS / PSI listeners and drives re-resolution; centralizing
 * cache-invalidation that way avoids a feedback loop between service and UI.
 *
 * <p>Lookup order matches GitHub's documented precedence —
 * {@code .github/CODEOWNERS} > {@code CODEOWNERS} > {@code docs/CODEOWNERS} —
 * with {@code .gitlab/CODEOWNERS} added at the end for GitLab projects.
 */
@Service(Service.Level.PROJECT)
public final class CodeownersService {

  private static final String[] CANDIDATE_PATHS = {
          ".github/CODEOWNERS",
          "CODEOWNERS",
          "docs/CODEOWNERS",
          ".gitlab/CODEOWNERS",
  };

  private final Project project;

  /**
   * Snapshot of the last parse. {@code null} means "not yet parsed".
   */
  private volatile Cache cache;

  public CodeownersService(@NotNull Project project) {
    this.project = project;
  }

  public static @NotNull CodeownersService getInstance(@NotNull Project project) {
    return project.getService(CodeownersService.class);
  }

  private static @Nullable VirtualFile findCodeownersUnder(@NotNull VirtualFile root) {
    for (String candidate : CANDIDATE_PATHS) {
      VirtualFile vf = root.findFileByRelativePath(candidate);
      if (vf != null && vf.isValid() && !vf.isDirectory()) return vf;
    }
    return null;
  }

  private static @Nullable String relativizeAgainst(@NotNull String filePath, @NotNull String rootPath) {
    if (filePath.equals(rootPath)) return "";
    String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
    if (!filePath.startsWith(prefix)) return null;
    return filePath.substring(prefix.length());
  }

  /**
   * Returns the owners that apply to {@code file} (last matching rule wins),
   * or {@link CodeownersOwnerResolution#NONE} if no rule applies, no CODEOWNERS
   * file exists, or {@code file} lies outside the project root.
   *
   * <p>Must be called under a read action — it accesses VFS and possibly
   * document state.
   */
  public @NotNull CodeownersOwnerResolution resolveOwners(@Nullable VirtualFile file) {
    if (file == null || project.isDisposed()) return CodeownersOwnerResolution.NONE;
    String relativePath = relativize(file);
    if (relativePath == null) return CodeownersOwnerResolution.NONE;

    List<CodeownersRule> rules = getRules();
    // Last-match-wins: walk in reverse and stop at the first rule that matches.
    for (int i = rules.size() - 1; i >= 0; i--) {
      CodeownersRule rule = rules.get(i);
      if (rule.matches(relativePath)) {
        return new CodeownersOwnerResolution(rule);
      }
    }
    return CodeownersOwnerResolution.NONE;
  }

  /**
   * @return the CODEOWNERS file that was used for the most recent resolution, or {@code null}.
   */
  public @Nullable VirtualFile getCodeownersFile() {
    Cache c = cache;
    return c == null ? null : c.sourceFile;
  }

  private @NotNull List<CodeownersRule> getRules() {
    VirtualFile codeownersFile = locateCodeownersFile();
    if (codeownersFile == null) {
      cache = null;
      return List.of();
    }
    long stamp = currentStamp(codeownersFile);
    Cache existing = cache;
    if (existing != null && existing.sourceFile.equals(codeownersFile) && existing.stamp == stamp) {
      return existing.rules;
    }
    CharSequence content = readContent(codeownersFile);
    List<CodeownersRule> rules = CodeownersRuleParser.parse(content, codeownersFile);
    cache = new Cache(codeownersFile, stamp, rules);
    return rules;
  }

  private @NotNull CharSequence readContent(@NotNull VirtualFile file) {
    Document doc = FileDocumentManager.getInstance().getDocument(file);
    if (doc != null) return doc.getCharsSequence();
    try {
      return new String(file.contentsToByteArray(), file.getCharset());
    } catch (Exception ignored) {
      return "";
    }
  }

  /**
   * Uses the document stamp when an editor has the file open (catches
   * unsaved edits), and the VFS stamp otherwise.
   */
  private long currentStamp(@NotNull VirtualFile file) {
    Document doc = FileDocumentManager.getInstance().getCachedDocument(file);
    return doc != null ? doc.getModificationStamp() : file.getModificationStamp();
  }

  private @Nullable VirtualFile locateCodeownersFile() {
    // Content roots first — works for multi-module setups and for in-memory
    // file systems used by light test fixtures.
    for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
      VirtualFile vf = findCodeownersUnder(root);
      if (vf != null) return vf;
    }
    // Fallback for projects without registered content roots (e.g. attached
    // directories): consult the on-disk base path via the LocalFileSystem.
    String basePath = project.getBasePath();
    if (basePath != null) {
      VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
      if (baseDir != null) return findCodeownersUnder(baseDir);
    }
    return null;
  }

  /**
   * Project-relative, forward-slash path with no leading {@code /} — the form
   * CODEOWNERS patterns are written against. Tries every content root before
   * falling back to {@link Project#getBasePath()}.
   */
  private @Nullable String relativize(@NotNull VirtualFile file) {
    String filePath = file.getPath();
    for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
      String rel = relativizeAgainst(filePath, root.getPath());
      if (rel != null) return rel;
    }
    String basePath = project.getBasePath();
    return basePath == null ? null : relativizeAgainst(filePath, basePath);
  }

  private record Cache(@NotNull VirtualFile sourceFile, long stamp, @NotNull List<CodeownersRule> rules) {
  }
}
