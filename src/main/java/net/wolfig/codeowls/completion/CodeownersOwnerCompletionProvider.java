package net.wolfig.codeowls.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.util.ProcessingContext;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Completion provider for the owner segment of CODEOWNERS rules.
 *
 * <p>Triggers only when the caret is past the rule's pattern token. Owner
 * names may start with characters the default identifier matcher discards
 * (most notably {@code @}, plus a {@code .} in {@code @first.last}), so the
 * provider overrides the platform prefix matcher with the partial owner the
 * user has typed so far — captured by
 * {@link CodeownersCompletionContext#typedSegmentText()}.
 *
 * <p>Candidates come from {@link CodeownersOwnerCollector}, which currently
 * surfaces owners already used elsewhere in the same CODEOWNERS file. The
 * type-text on each lookup element names the source so the user can tell at a
 * glance where a suggestion came from.
 */
public final class CodeownersOwnerCompletionProvider extends CompletionProvider<CompletionParameters> {

  private final CodeownersOwnerCollector collector;

  public CodeownersOwnerCompletionProvider() {
    this(new CodeownersOwnerCollector());
  }

  /**
   * Test seam: supply a collector with custom sources.
   */
  public CodeownersOwnerCompletionProvider(@NotNull CodeownersOwnerCollector collector) {
    this.collector = collector;
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters params,
                                @NotNull ProcessingContext ctx,
                                @NotNull CompletionResultSet result) {
    if (!params.getOriginalFile().getLanguage().is(CodeownersLanguage.INSTANCE)) return;

    Document doc = params.getEditor().getDocument();
    int offset = params.getOffset();
    int lineNumber = doc.getLineNumber(offset);
    int lineStart = doc.getLineStartOffset(lineNumber);
    CharSequence linePrefix = doc.getCharsSequence().subSequence(lineStart, offset);

    CodeownersCompletionContext context = CodeownersCompletionContext.fromLinePrefix(linePrefix);
    if (context.segment() != CodeownersCompletionContext.Segment.OWNER) return;

    CompletionResultSet scoped = result.withPrefixMatcher(context.typedSegmentText());
    for (CodeownersOwnerCollector.OwnerCandidate candidate : collector.collect(params.getOriginalFile())) {
      // Don't echo back exactly what the user already typed.
      if (candidate.owner().equals(context.typedSegmentText())) continue;
      scoped.addElement(LookupElementBuilder.create(candidate.owner())
              .withTypeText(candidate.source(), true));
    }
  }
}
