package net.wolfig.codeowls.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ProcessingContext;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Completion provider for the path / pattern segment of CODEOWNERS rules.
 *
 * <p>When the caret sits inside the pattern token of a CODEOWNERS line we
 * suggest <em>direct children</em> of the directory the typed prefix names —
 * the same model the IDE uses for path completion in import statements. The
 * user types {@code src/} and gets the entries under {@code src/}; types
 * {@code src/ma} and only entries starting with {@code ma} survive the
 * platform's prefix matcher.
 *
 * <p>Directories are appended with a trailing {@code /} and re-trigger
 * completion after insertion, so drilling into a path takes one Tab per level.
 *
 * <p>Paths are always project-relative; the provider walks from the first
 * project content root that contains a CODEOWNERS file or, as a fallback, from
 * the project base directory. Known noise directories (VCS metadata, IDE
 * caches, common build outputs) are filtered. Suggestions are computed
 * lazily — one VFS {@code getChildren} call per Ctrl-Space — so no caching
 * layer is required and project edits show up immediately.
 */
public final class CodeownersPathCompletionProvider extends CompletionProvider<CompletionParameters> {

  /**
   * Directory names that are never useful to suggest in a CODEOWNERS rule.
   */
  static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
          ".git",
          ".idea",
          ".gradle",
          "node_modules",
          "build",
          "dist",
          "target",
          "out");

  private static boolean isCodeownersFile(@NotNull CompletionParameters params) {
    return params.getOriginalFile().getLanguage().is(CodeownersLanguage.INSTANCE);
  }

  /**
   * Pick a project root for path suggestions by walking up from the CODEOWNERS
   * file. A CODEOWNERS at {@code .github/CODEOWNERS}, {@code .gitlab/CODEOWNERS},
   * or {@code docs/CODEOWNERS} lives one level below the directory its
   * patterns are written against; a bare {@code CODEOWNERS} sits in that
   * directory itself. We deliberately walk relative to the file rather than
   * trusting {@code ProjectRootManager} so the suggestions stay correct when
   * the project's content roots and the file's on-disk parent diverge — the
   * common case in IntelliJ light test fixtures and a sensible default in
   * monorepos where multiple CODEOWNERS files coexist.
   */
  private static @Nullable VirtualFile projectRoot(@Nullable VirtualFile contextFile) {
    if (contextFile == null) return null;
    VirtualFile parent = contextFile.getParent();
    if (parent == null) return null;
    String parentName = parent.getName();
    if (parentName.equals(".github") || parentName.equals(".gitlab") || parentName.equals("docs")) {
      VirtualFile gp = parent.getParent();
      return gp != null ? gp : parent;
    }
    return parent;
  }

  private static @Nullable VirtualFile resolveTargetDir(@NotNull VirtualFile root,
                                                        @NotNull String dirPart) {
    if (dirPart.isEmpty()) return root;
    String rel = dirPart.startsWith("/") ? dirPart.substring(1) : dirPart;
    if (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
    if (rel.isEmpty()) return root;
    return root.findFileByRelativePath(rel);
  }

  private static @NotNull LookupElementBuilder toLookupElement(@NotNull VirtualFile child) {
    boolean dir = child.isDirectory();
    String lookup = dir ? child.getName() + "/" : child.getName();
    LookupElementBuilder builder = LookupElementBuilder.create(lookup)
            .withIcon(dir ? AllIcons.Nodes.Folder : child.getFileType().getIcon());
    if (dir) {
      // Re-open the popup so the user can keep drilling into subdirectories
      // without manually triggering completion at each level.
      builder = builder.withInsertHandler((insertion, item) ->
              AutoPopupController.getInstance(insertion.getProject())
                      .scheduleAutoPopup(insertion.getEditor()));
    }
    return builder;
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters params,
                                @NotNull ProcessingContext ctx,
                                @NotNull CompletionResultSet result) {
    if (!isCodeownersFile(params)) return;

    Document doc = params.getEditor().getDocument();
    int offset = params.getOffset();
    int lineNumber = doc.getLineNumber(offset);
    int lineStart = doc.getLineStartOffset(lineNumber);
    CharSequence linePrefix = doc.getCharsSequence().subSequence(lineStart, offset);

    CodeownersCompletionContext context = CodeownersCompletionContext.fromLinePrefix(linePrefix);
    if (context.segment() != CodeownersCompletionContext.Segment.PATTERN) return;

    String typed = context.typedSegmentText();

    // Split the typed text into a "directory part" (everything up to and
    // including the last '/') and an "entry prefix" (what comes after it).
    // We override the platform prefix matcher with the entry prefix so the
    // candidates — direct child names — line up with what the user is typing.
    int lastSlash = typed.lastIndexOf('/');
    String dirPart = lastSlash >= 0 ? typed.substring(0, lastSlash + 1) : "";
    String entryPrefix = lastSlash >= 0 ? typed.substring(lastSlash + 1) : typed;
    CompletionResultSet scoped = result.withPrefixMatcher(entryPrefix);

    VirtualFile projectRoot = projectRoot(params.getOriginalFile().getVirtualFile());
    if (projectRoot == null) return;

    VirtualFile targetDir = resolveTargetDir(projectRoot, dirPart);
    if (targetDir == null || !targetDir.isDirectory()) return;

    for (VirtualFile child : targetDir.getChildren()) {
      if (!child.isValid()) continue;
      if (child.isDirectory() && IGNORED_DIRECTORY_NAMES.contains(child.getName())) continue;
      scoped.addElement(toLookupElement(child));
    }
  }
}
