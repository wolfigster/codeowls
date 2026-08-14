package net.wolfig.codeowls.suggestion;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import net.wolfig.codeowls.completion.CodeownersGitContributorService;
import net.wolfig.codeowls.completion.CodeownersOwnerCollector;
import net.wolfig.codeowls.completion.CodeownersOwnerCollector.OwnerCandidate;
import net.wolfig.codeowls.entry.CodeownersEntryRule;
import net.wolfig.codeowls.entry.CodeownersEntryWriter;
import net.wolfig.codeowls.statusbar.CodeownersService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;

/**
 * Computes and shows owner suggestions for a file that has no CODEOWNERS owner.
 *
 * <p>Triggered by clicking the status-bar widget. Resolving the CODEOWNERS
 * context, gathering completion candidates, and especially the per-file
 * {@code git log} all happen on a background task; the chooser popup is then
 * shown on the EDT. Picking a suggestion appends a rule
 * ({@code /relative/path @owner}) to the CODEOWNERS file and opens it there.
 *
 * <p>If no CODEOWNERS file governs the target, nothing is shown — there would be
 * nowhere to add the rule.
 */
public final class CodeownersSuggestionPopup {

  private CodeownersSuggestionPopup() {
  }

  public static void show(@NotNull Project project, @NotNull VirtualFile target, @NotNull RelativePoint where) {
    new Task.Backgroundable(project, "Computing CODEOWNERS Suggestions", true) {
      private List<OwnerSuggestion> suggestions = List.of();
      private CodeownersService.FileContext context;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        CodeownersService.FileContext ctx =
                ReadAction.compute(() -> CodeownersService.getInstance(project).fileContext(target));
        if (ctx == null) return;
        context = ctx;

        List<OwnerCandidate> candidates = ReadAction.compute(() -> {
          PsiFile psi = PsiManager.getInstance(project).findFile(ctx.codeownersFile());
          return psi == null ? List.<OwnerCandidate>of() : new CodeownersOwnerCollector().collect(psi);
        });

        Map<String, Integer> authors =
                CodeownersGitContributorService.getInstance(project).getFileAuthorCommits(target);

        suggestions = CodeownersOwnerSuggester.suggest(
                ctx.relativePath(), ctx.rules(), candidates, authors);
      }

      @Override
      public void onSuccess() {
        if (project.isDisposed() || context == null || suggestions.isEmpty()) return;
        showChooser(project, context, suggestions, where);
      }
    }.queue();
  }

  private static void showChooser(@NotNull Project project,
                                  @NotNull CodeownersService.FileContext context,
                                  @NotNull List<OwnerSuggestion> suggestions,
                                  @NotNull RelativePoint where) {
    JBPopupFactory.getInstance()
            .createPopupChooserBuilder(suggestions)
            .setTitle("Suggested Owners for " + context.relativePath())
            .setRenderer(new ColoredListCellRenderer<OwnerSuggestion>() {
              @Override
              protected void customizeCellRenderer(@NotNull JList<? extends OwnerSuggestion> list,
                                                   OwnerSuggestion value, int index,
                                                   boolean selected, boolean hasFocus) {
                if (value != null) append(format(value));
              }
            })
            .setItemChosenCallback(suggestion ->
                    insertRule(project, context.codeownersFile(), context.relativePath(), suggestion.owner()))
            .createPopup()
            .show(where);
  }

  /**
   * e.g. {@code "@api-team — 78%  ·  already used in CODEOWNERS"}
   */
  static @NotNull String format(@NotNull OwnerSuggestion s) {
    return s.owner() + "  —  " + s.confidencePercent() + "%  ·  " + s.source();
  }

  private static void insertRule(@NotNull Project project, @NotNull VirtualFile codeownersFile,
                                 @NotNull String relativePath, @NotNull String owner) {
    String pattern = CodeownersEntryRule.pattern(
            relativePath, CodeownersEntryRule.PathMode.EXACT);
    String rule = CodeownersEntryRule.build(pattern, owner);
    if (rule != null) {
      CodeownersEntryWriter.appendAndNavigate(project, codeownersFile, rule);
    }
  }
}
