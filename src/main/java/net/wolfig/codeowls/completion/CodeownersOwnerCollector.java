package net.wolfig.codeowls.completion;

import com.intellij.psi.PsiFile;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers owner suggestions from every source the plugin knows about and
 * de-duplicates them. The first source that mentions an owner wins, so
 * candidates are stable across invocations.
 *
 * <p>The structure makes it cheap to plug additional sources in later — a
 * Git-history walker, a plugin-settings-backed static list — without touching
 * the completion provider. v1 ships with the "current file" source only;
 * unavailable sources gracefully return no candidates.
 */
public final class CodeownersOwnerCollector {

  public static final String SOURCE_CURRENT_FILE = "already used in CODEOWNERS";
  public static final String SOURCE_GIT_HISTORY = "from Git history";
  public static final String SOURCE_PLUGIN_SETTINGS = "from plugin settings";
  public static final String SOURCE_BUILTIN_ROLE = "GitLab role";
  private final List<Source> sources;

  public CodeownersOwnerCollector() {
    this(List.of(new CurrentFileSource(), new CodeownersGitOwnerSource(), new BuiltinRoleSource()));
  }

  /**
   * Test seam: supply a custom set of sources.
   */
  public CodeownersOwnerCollector(@NotNull List<Source> sources) {
    this.sources = List.copyOf(sources);
  }

  public @NotNull List<OwnerCandidate> collect(@NotNull PsiFile codeownersFile) {
    Map<String, OwnerCandidate> byOwner = new LinkedHashMap<>();
    for (Source source : sources) {
      for (OwnerCandidate candidate : source.collect(codeownersFile)) {
        byOwner.putIfAbsent(candidate.owner(), candidate);
      }
    }
    return new ArrayList<>(byOwner.values());
  }

  /**
   * Provides {@link OwnerCandidate}s. Implementations are expected to be
   * fast and side-effect-free; sources that require background work should
   * cache and return an empty list when results aren't ready yet.
   */
  public interface Source {
    @NotNull List<OwnerCandidate> collect(@NotNull PsiFile codeownersFile);
  }

  /**
   * One owner string plus a short tail describing where it came from. The
   * source string is shown verbatim as the lookup element's type-text.
   */
  public record OwnerCandidate(@NotNull String owner, @NotNull String source) {
  }

  /**
   * Collects every owner mentioned in the CODEOWNERS file being edited. This
   * is the highest-signal source — owners listed elsewhere in the same file
   * are almost always the right completion for a new rule.
   */
  public static final class CurrentFileSource implements Source {
    @Override
    public @NotNull List<OwnerCandidate> collect(@NotNull PsiFile codeownersFile) {
      CharSequence text = codeownersFile.getViewProvider().getContents();
      if (text.isEmpty()) return List.of();
      List<CodeownersRule> rules = CodeownersRuleParser.parse(text, codeownersFile.getVirtualFile());
      List<OwnerCandidate> out = new ArrayList<>();
      for (CodeownersRule rule : rules) {
        for (String owner : rule.owners()) {
          out.add(new OwnerCandidate(owner, SOURCE_CURRENT_FILE));
        }
      }
      return out;
    }
  }

  /**
   * Surfaces the GitLab project roles ({@code @@developer}, {@code @@maintainer},
   * {@code @@owner}) as completion candidates. They're always available so
   * users don't need to remember the exact spelling or that two {@code @} signs
   * are required.
   */
  public static final class BuiltinRoleSource implements Source {
    private static final List<OwnerCandidate> ROLES = List.of(
            new OwnerCandidate("@@developer", SOURCE_BUILTIN_ROLE),
            new OwnerCandidate("@@maintainer", SOURCE_BUILTIN_ROLE),
            new OwnerCandidate("@@owner", SOURCE_BUILTIN_ROLE));

    @Override
    public @NotNull List<OwnerCandidate> collect(@NotNull PsiFile codeownersFile) {
      return ROLES;
    }
  }
}
