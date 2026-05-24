package net.wolfig.codeowls.inspection;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import net.wolfig.codeowls.inlay.CodeownersMatchCounter;
import net.wolfig.codeowls.inspection.CodeownersRedundancyAnalyzer.Finding;
import net.wolfig.codeowls.lang.CodeownersLanguage;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks CODEOWNERS rules that carry no weight: a pattern that matches no file
 * in the project (the path/glob does not exist), or a rule that is fully
 * shadowed by a later rule and therefore never decides ownership.
 *
 * <p>A pattern matching no file is reported as a warning (yellow underline) —
 * it is likely a mistake; a shadowed rule is dead code, so it gets the
 * greyed-out unused style. Both carry a quick fix that deletes the rule line.
 * The analysis ({@link CodeownersRedundancyAnalyzer}) walks the project tree,
 * so its result is cached on the CODEOWNERS {@link PsiFile} and recomputed only
 * when the PSI changes.
 */
public final class CodeownersUnnecessaryRuleInspection extends LocalInspectionTool {

  /**
   * A non-existent path is a likely mistake worth a warning (yellow underline);
   * a shadowed rule is merely dead code, so it gets the greyed-out unused style.
   */
  private static @NotNull ProblemHighlightType highlightType(@NotNull CodeownersRedundancyAnalyzer.Kind kind) {
    return switch (kind) {
      case NO_FILES_MATCH -> ProblemHighlightType.WARNING;
      case SHADOWED -> ProblemHighlightType.LIKE_UNUSED_SYMBOL;
    };
  }

  private static @NotNull String message(@NotNull Finding finding, @NotNull List<CodeownersRule> rules) {
    return switch (finding.kind()) {
      case NO_FILES_MATCH -> "Pattern matches no files in the project";
      case SHADOWED -> "Rule is shadowed by the rule on line "
              + (rules.get(finding.shadowingRuleIndex()).lineNumber() + 1)
              + " and never takes effect";
    };
  }

  private static @NotNull Analysis analysisFor(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () ->
            CachedValueProvider.Result.create(
                    compute(file), file, PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @NotNull Analysis compute(@NotNull PsiFile file) {
    VirtualFile vf = file.getVirtualFile();
    VirtualFile root = CodeownersMatchCounter.projectRoot(vf);
    List<CodeownersRule> rules = CodeownersRuleParser.parse(file.getViewProvider().getContents(), vf);
    if (root == null || rules.isEmpty()) return new Analysis(rules, List.of());
    List<String> paths = CodeownersMatchCounter.collectProjectFilePaths(root);
    return new Analysis(rules, CodeownersRedundancyAnalyzer.analyze(rules, paths));
  }

  private static @NotNull List<PsiElement> collectPatternTokens(@NotNull PsiFile file) {
    List<PsiElement> tokens = new ArrayList<>();
    PsiTreeUtil.processElements(file, (PsiElementProcessor<PsiElement>) element -> {
      ASTNode node = element.getNode();
      if (node != null && node.getElementType() == CodeownersTokenTypes.PATTERN) {
        tokens.add(element);
      }
      return true;
    });
    return tokens;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                  @NotNull InspectionManager manager,
                                                  boolean isOnTheFly) {
    if (!file.getLanguage().is(CodeownersLanguage.INSTANCE)) return null;

    Analysis analysis = analysisFor(file);
    if (analysis.findings().isEmpty()) return ProblemDescriptor.EMPTY_ARRAY;

    List<PsiElement> patternTokens = collectPatternTokens(file);
    // Defensive: rules and PATTERN tokens are produced from the same content,
    // so they must align 1:1. If they somehow don't, skip rather than mis-mark.
    if (patternTokens.size() != analysis.rules().size()) return ProblemDescriptor.EMPTY_ARRAY;

    List<ProblemDescriptor> problems = new ArrayList<>(analysis.findings().size());
    for (Finding finding : analysis.findings()) {
      PsiElement token = patternTokens.get(finding.ruleIndex());
      problems.add(manager.createProblemDescriptor(
              token,
              message(finding, analysis.rules()),
              new RemoveRuleQuickFix(),
              highlightType(finding.kind()),
              isOnTheFly));
    }
    return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private record Analysis(@NotNull List<CodeownersRule> rules, @NotNull List<Finding> findings) {
  }

  /**
   * Deletes the whole line of the flagged rule. Runs in the platform-provided
   * write action, so it edits the document directly.
   */
  private static final class RemoveRuleQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return "Remove CODEOWNERS rule";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null || !element.isValid()) return;
      PsiFile file = element.getContainingFile();
      if (file == null) return;
      Document document = file.getViewProvider().getDocument();
      if (document == null) return;

      int line = document.getLineNumber(element.getTextRange().getStartOffset());
      int start = document.getLineStartOffset(line);
      int end = line + 1 < document.getLineCount()
              ? document.getLineStartOffset(line + 1)
              : document.getTextLength();
      document.deleteString(start, end);
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
  }
}
