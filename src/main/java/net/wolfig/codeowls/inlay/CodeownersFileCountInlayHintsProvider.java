package net.wolfig.codeowls.inlay;

import com.intellij.codeInsight.hints.declarative.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Declarative inlay-hints provider that decorates each glob-style CODEOWNERS
 * rule with the count of project files it matches, e.g. {@code 14 files}.
 *
 * <p>The hint is anchored as an {@link InlineInlayPosition} right after the
 * pattern token (before the owners), so a line like
 * {@code *.ts @frontend-team} renders as {@code *.ts 14 files @frontend-team}
 * rather than putting the count at the far right of the line where the
 * relationship to the pattern is less obvious.
 *
 * <p>Registered against the CODEOWNERS language as a
 * {@code <codeInsight.declarativeInlayProvider>} so it shows up in
 * <em>Settings | Editor | Inlay Hints</em> with its own toggle.
 *
 * <p>The heavy lifting lives in {@link CodeownersMatchCounter}: a single
 * project-tree walk per CODEOWNERS file, cached via {@link
 * com.intellij.psi.util.CachedValuesManager} and invalidated by
 * {@link com.intellij.psi.util.PsiModificationTracker#MODIFICATION_COUNT}.
 * Repaints reuse the cached counts without re-running file-system matches.
 *
 * <p>Rules that already pin down a single file (exact-path patterns) and
 * lines that aren't rules at all (comments, blank lines, section headers)
 * are skipped — the match-counter returns them as "no entry", and we don't
 * synthesize a hint.
 */
public final class CodeownersFileCountInlayHintsProvider implements InlayHintsProvider {

  /**
   * Pulled out and package-private so tests can pin singular/plural wording.
   */
  static @NotNull String formatHintText(int count) {
    return count + (count == 1 ? " file" : " files");
  }

  /**
   * @return the offset within {@code lineText} immediately after the pattern
   * token — leading whitespace is skipped, then the first whitespace-delimited
   * token's end is returned. Returns {@code 0} when the line has no pattern at
   * all (empty or whitespace-only) — the collector uses that as a "no anchor"
   * sentinel and skips the inlay. Package-private for testing.
   */
  static int patternEndOffsetInLine(@NotNull CharSequence lineText) {
    int i = 0;
    int n = lineText.length();
    while (i < n && isHSpace(lineText.charAt(i))) i++;
    if (i == n) return 0;
    while (i < n && !isHSpace(lineText.charAt(i))) i++;
    return i;
  }

  private static boolean isHSpace(char c) {
    return c == ' ' || c == '\t';
  }

  /**
   * Adapter from Java to the Kotlin lambda the platform expects.
   * {@link PresentationTreeBuilder#text} takes {@code (String, InlayActionData)};
   * a {@code null} action keeps the hint as a plain text decoration.
   */
  private static @NotNull Function1<PresentationTreeBuilder, Unit> hintBuilder(@NotNull String text) {
    return builder -> {
      builder.text(text, null);
      return Unit.INSTANCE;
    };
  }

  @Override
  public @Nullable InlayHintsCollector createCollector(@NotNull PsiFile file,
                                                       @NotNull Editor editor) {
    if (!file.getLanguage().is(CodeownersLanguage.INSTANCE)) return null;
    return new Collector();
  }

  /**
   * The collector iterates the whole CODEOWNERS file in one shot. We don't
   * need element-by-element bypass — per-line counts are precomputed and
   * indexed by line number; the inlay position is then resolved against the
   * editor document to land just past the pattern token on each line.
   */
  private static final class Collector implements OwnBypassCollector {

    @Override
    public void collectHintsForFile(@NotNull PsiFile file, @NotNull InlayTreeSink sink) {
      Map<Integer, Integer> counts = CodeownersMatchCounter.countsByLine(file);
      if (counts.isEmpty()) return;
      Document document = file.getViewProvider().getDocument();
      if (document == null) return;
      HintFormat format = HintFormat.Companion.getDefault();
      CharSequence docText = document.getCharsSequence();
      for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
        int line = entry.getKey();
        if (line < 0 || line >= document.getLineCount()) continue;
        int lineStart = document.getLineStartOffset(line);
        int lineEnd = document.getLineEndOffset(line);
        int patternEndInLine = patternEndOffsetInLine(docText.subSequence(lineStart, lineEnd));
        if (patternEndInLine == 0) continue;
        int offset = lineStart + patternEndInLine;
        int count = entry.getValue();
        // relatedToPrevious = true: the hint belongs to the pattern token to
        // its left, so cursor / selection treat them as one unit.
        sink.addPresentation(
                new InlineInlayPosition(offset, /* relatedToPrevious = */ true, /* priority = */ 0),
                /* payloads = */ null,
                /* tooltip  = */ null,
                format,
                hintBuilder(formatHintText(count)));
      }
    }
  }
}
