package net.wolfig.codeowls.matcher;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A parsed GitLab section header, e.g. {@code [Backend][2] @org/backend @alice}
 * or {@code ^[Optional]}.
 *
 * <p>Attached to every {@link CodeownersRule} that falls under the section so
 * that an ownership explanation can show <em>why</em> a rule has the owners and
 * approval count it does — the inheritance that {@link CodeownersRuleParser}
 * otherwise bakes irreversibly into {@link CodeownersRule#owners()}.
 *
 * @param name          the section name between the brackets (e.g. {@code Backend})
 * @param optional      {@code true} for a {@code ^[Section]} optional section
 * @param defaultOwners owners declared on the header line, inherited by rules
 *                      in the section that name none of their own
 * @param approvalCount the section's required approval count ({@code [Backend][2]}),
 *                      or {@code null} when the header declares none
 */
public record CodeownersSection(
        @NotNull String name,
        boolean optional,
        @NotNull List<String> defaultOwners,
        @Nullable Integer approvalCount) {

  /**
   * Renders the header roughly as it appears in the file, e.g.
   * {@code ^[Backend][2] @org/backend}. Used for display in the ownership
   * explanation.
   */
  public @NotNull String displayHeader() {
    StringBuilder sb = new StringBuilder();
    if (optional) sb.append('^');
    sb.append('[').append(name).append(']');
    if (approvalCount != null) sb.append('[').append(approvalCount).append(']');
    for (String owner : defaultOwners) sb.append(' ').append(owner);
    return sb.toString();
  }
}
