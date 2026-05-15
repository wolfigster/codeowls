package net.wolfig.codeowls.completion;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Owner-completion source backed by Git history.
 *
 * <p>Reads from {@link CodeownersGitContributorService}, which caches the
 * unique author emails returned by {@code git log}. The service refreshes in
 * the background; this source just consumes whatever's cached, so the
 * completion popup is never blocked on an external process.
 *
 * <p>Each contributor is surfaced as an {@link CodeownersOwnerCollector.OwnerCandidate}
 * tagged with {@link CodeownersOwnerCollector#SOURCE_GIT_HISTORY}, so the
 * collector's dedup logic gives precedence to owners already used elsewhere
 * in the CODEOWNERS file (the "current file" source comes first in the
 * default ordering).
 */
public final class CodeownersGitOwnerSource implements CodeownersOwnerCollector.Source {

  private final Function<Project, List<String>> contributorsProvider;

  public CodeownersGitOwnerSource() {
    this(p -> CodeownersGitContributorService.getInstance(p).getCachedContributors());
  }

  /**
   * Test seam: supply contributors directly without hitting the service / Git.
   */
  CodeownersGitOwnerSource(@NotNull Function<Project, List<String>> contributorsProvider) {
    this.contributorsProvider = contributorsProvider;
  }

  @Override
  public @NotNull List<CodeownersOwnerCollector.OwnerCandidate> collect(@NotNull PsiFile codeownersFile) {
    Project project = codeownersFile.getProject();
    List<String> contributors = contributorsProvider.apply(project);
    if (contributors.isEmpty()) return List.of();
    List<CodeownersOwnerCollector.OwnerCandidate> out = new ArrayList<>(contributors.size());
    for (String email : contributors) {
      out.add(new CodeownersOwnerCollector.OwnerCandidate(
              email, CodeownersOwnerCollector.SOURCE_GIT_HISTORY));
    }
    return out;
  }
}
