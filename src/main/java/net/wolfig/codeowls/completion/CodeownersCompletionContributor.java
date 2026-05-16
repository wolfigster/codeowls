package net.wolfig.codeowls.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import net.wolfig.codeowls.lang.CodeownersLanguage;

/**
 * Entry point for CODEOWNERS completion. The contributor delegates to two
 * focused providers — one for the path segment, one for the owner segment.
 * Each provider checks the segment itself (via
 * {@link CodeownersCompletionContext}) and bails out when the caret is in a
 * place it doesn't own; that keeps registration trivial here.
 */
public final class CodeownersCompletionContributor extends CompletionContributor {

  private static final ElementPattern<PsiElement> CODEOWNERS_ELEMENT =
          PlatformPatterns.psiElement().inFile(
                  PlatformPatterns.psiFile()
                          .withLanguage(CodeownersLanguage.INSTANCE)
          );

  public CodeownersCompletionContributor() {
    extend(CompletionType.BASIC, CODEOWNERS_ELEMENT, new CodeownersPathCompletionProvider());
    extend(CompletionType.BASIC, CODEOWNERS_ELEMENT, new CodeownersOwnerCompletionProvider());
    extend(CompletionType.BASIC, CODEOWNERS_ELEMENT, new CodeownersSectionCompletionProvider());
  }
}
