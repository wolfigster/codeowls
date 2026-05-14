package net.wolfig.codeowls.statusbar;

import net.wolfig.codeowls.matcher.CodeownersRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of resolving CODEOWNERS for one file: the matched rule, or
 * {@link #NONE} when no rule (or no CODEOWNERS file at all) applies.
 *
 * <p>Returned by {@link CodeownersService#resolveOwners} and consumed by the
 * status bar widget. Keeping it immutable means it is safe to pass between
 * the background read action and the UI thread.
 */
public record CodeownersOwnerResolution(@Nullable CodeownersRule rule) {

  public static final CodeownersOwnerResolution NONE = new CodeownersOwnerResolution(null);

  public boolean isEmpty() {
    return rule == null;
  }

  public @NotNull List<String> owners() {
    return rule == null ? Collections.emptyList() : rule.owners();
  }
}
