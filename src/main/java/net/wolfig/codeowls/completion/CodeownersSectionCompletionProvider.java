package net.wolfig.codeowls.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.util.ProcessingContext;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Completion provider for the name part of a GitLab section header — fires
 * only when the caret sits inside the first {@code [...]} of a section line.
 *
 * <p>Suggests section names already declared elsewhere in the current
 * CODEOWNERS file so that a project's section taxonomy stays consistent:
 * picking from existing names is almost always the right thing.
 */
public final class CodeownersSectionCompletionProvider extends CompletionProvider<CompletionParameters> {

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
    if (context.segment() != CodeownersCompletionContext.Segment.SECTION_HEADER_NAME) return;

    CompletionResultSet scoped = result.withPrefixMatcher(context.typedSegmentText());
    List<String> sections = CodeownersSectionCollector.collect(params.getOriginalFile());
    for (String name : sections) {
      if (name.equals(context.typedSegmentText())) continue;
      scoped.addElement(LookupElementBuilder.create(name));
    }
  }
}
