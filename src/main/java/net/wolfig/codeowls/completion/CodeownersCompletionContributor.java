package net.wolfig.codeowls.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for CODEOWNERS completion. The contributor delegates to two
 * focused providers — one for the path segment, one for the owner segment.
 * Each provider checks the segment itself (via
 * {@link CodeownersCompletionContext}) and bails out when the caret is in a
 * place it doesn't own; that keeps registration trivial here.
 */
public final class CodeownersCompletionContributor extends CompletionContributor {

  public CodeownersCompletionContributor() {
    extend(CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile()
                    .withLanguage(net.wolfig.codeowls.lang.CodeownersLanguage.INSTANCE)),
            new CodeownersPathCompletionProvider());
    extend(CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile()
                    .withLanguage(net.wolfig.codeowls.lang.CodeownersLanguage.INSTANCE)),
            new CodeownersOwnerCompletionProvider());
    extend(CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile()
                    .withLanguage(net.wolfig.codeowls.lang.CodeownersLanguage.INSTANCE)),
            new CodeownersSectionCompletionProvider());
  }

  /**
   * Open the lookup popup automatically when the user types {@code @} so the
   * owner suggestions appear without having to press Ctrl+Space first.
   */
  @Override
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typedChar) {
    return typedChar == '@'
            && position.getContainingFile().getLanguage().is(net.wolfig.codeowls.lang.CodeownersLanguage.INSTANCE);
  }
}
