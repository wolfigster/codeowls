package net.wolfig.codeowls.statusbar;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import net.wolfig.codeowls.explain.OwnershipExplanation;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
 * <p>For a given file the service walks up its parent chain looking for
 * {@code .github/CODEOWNERS}, {@code CODEOWNERS}, {@code docs/CODEOWNERS}, and
 * {@code .gitlab/CODEOWNERS} (GitHub's documented precedence, plus the GitLab
 * location). The first hit wins, which makes monorepos with per-module
 * CODEOWNERS files behave the way {@code git} does and keeps the lookup
 * independent of how the project's content roots happen to be registered.
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

  private static @Nullable Located locateFor(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    while (dir != null) {
      for (String candidate : CANDIDATE_PATHS) {
        VirtualFile vf = dir.findFileByRelativePath(candidate);
        if (vf != null && vf.isValid() && !vf.isDirectory()) {
          return new Located(vf, dir);
        }
      }
      dir = dir.getParent();
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
   * The rules that match {@code relativePath}, in file (evaluation) order. The
   * single source of truth for "which rules apply" — both resolution and
   * explanation go through here so they cannot disagree.
   */
  private static @NotNull List<CodeownersRule> matchingRules(@NotNull List<CodeownersRule> rules,
                                                             @NotNull String relativePath) {
    List<CodeownersRule> matching = new ArrayList<>();
    for (CodeownersRule rule : rules) {
      if (rule.matches(relativePath)) matching.add(rule);
    }
    return matching;
  }

  /**
   * Returns the owners that apply to {@code file} (last matching rule wins),
   * or {@link CodeownersOwnerResolution#NONE} if no rule applies or no
   * CODEOWNERS file is reachable from {@code file}'s ancestors.
   *
   * <p>Must be called under a read action — it accesses VFS and possibly
   * document state.
   */
  public @NotNull CodeownersOwnerResolution resolveOwners(@Nullable VirtualFile file) {
    EffectiveOwnership ownership = resolveOwnership(file);
    return ownership.isEmpty()
            ? CodeownersOwnerResolution.NONE
            : new CodeownersOwnerResolution(ownership.rule());
  }

  /**
   * Resolves the complete effective ownership context for {@code file}.
   * This is the central API for features that need both the winning rule and
   * the governing CODEOWNERS file/root.
   *
   * <p>Must be called under a read action.
   */
  public @NotNull EffectiveOwnership resolveOwnership(@Nullable VirtualFile file) {
    ResolvedFile resolved = resolve(file);
    if (resolved == null) return EffectiveOwnership.NONE;
    CodeownersRule rule = resolved.matching.isEmpty()
            ? null
            : resolved.matching.getLast();
    return new EffectiveOwnership(
            resolved.located.codeownersFile,
            resolved.located.root,
            resolved.relativePath,
            rule);
  }

  /**
   * Explains the CODEOWNERS ownership of {@code file}: every rule that matches
   * it (in evaluation order) and which one wins. Shares its matching pass with
   * {@link #resolveOwners} via {@link #matchingRules}, so the effective owner in
   * the returned {@link OwnershipExplanation} always agrees with the status bar
   * widget and owner-lookup logic.
   *
   * <p>Must be called under a read action — it accesses VFS and possibly
   * document state.
   */
  public @NotNull OwnershipExplanation explain(@NotNull VirtualFile file) {
    ResolvedFile resolved = resolve(file);
    return resolved == null
            ? OwnershipExplanation.noCodeowners(file)
            : OwnershipExplanation.of(
            file,
            resolved.relativePath,
            resolved.located.codeownersFile,
            resolved.matching);
  }

  private @Nullable ResolvedFile resolve(@Nullable VirtualFile file) {
    if (file == null || project.isDisposed()) return null;
    Located located = locateFor(file);
    if (located == null) return null;
    String relativePath = relativizeAgainst(file.getPath(), located.root.getPath());
    if (relativePath == null) return null;
    return new ResolvedFile(
            located,
            relativePath,
            matchingRules(getRules(located.codeownersFile), relativePath));
  }

  /**
   * Resolves the CODEOWNERS context for {@code file}: the governing CODEOWNERS
   * file, the file's path relative to that CODEOWNERS file's root, and the
   * parsed rules. Returns {@code null} when no CODEOWNERS file is reachable.
   *
   * <p>Used by owner-suggestion: it needs the repo-relative path (to build a
   * new rule and to score path proximity) and the rules, all anchored to the
   * same root the resolver uses. Must be called under a read action.
   */
  public @Nullable FileContext fileContext(@Nullable VirtualFile file) {
    if (file == null || project.isDisposed()) return null;
    Located located = locateFor(file);
    if (located == null) return null;
    String relativePath = relativizeAgainst(file.getPath(), located.root.getPath());
    if (relativePath == null || relativePath.isEmpty()) return null;
    return new FileContext(located.codeownersFile, relativePath, getRules(located.codeownersFile));
  }

  /**
   * @return the CODEOWNERS file that was used for the most recent resolution, or {@code null}.
   */
  public @Nullable VirtualFile getCodeownersFile() {
    Cache c = cache;
    return c == null ? null : c.sourceFile;
  }

  private @NotNull List<CodeownersRule> getRules(@NotNull VirtualFile codeownersFile) {
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

  private record Located(@NotNull VirtualFile codeownersFile, @NotNull VirtualFile root) {
  }

  private record ResolvedFile(@NotNull Located located,
                              @NotNull String relativePath,
                              @NotNull List<CodeownersRule> matching) {
  }

  /**
   * The CODEOWNERS context for a target file: its governing CODEOWNERS file, its
   * repo-relative path, and the parsed rules.
   */
  public record FileContext(@NotNull VirtualFile codeownersFile,
                            @NotNull String relativePath,
                            @NotNull List<CodeownersRule> rules) {
  }

  private record Cache(@NotNull VirtualFile sourceFile, long stamp, @NotNull List<CodeownersRule> rules) {
  }
}
