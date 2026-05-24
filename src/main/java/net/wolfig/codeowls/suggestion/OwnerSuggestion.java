package net.wolfig.codeowls.suggestion;

import org.jetbrains.annotations.NotNull;

/**
 * A single owner suggestion for a file that currently has no CODEOWNERS owner:
 * the owner string, the source it was drawn from (the same short tail the
 * completion popup shows), and a confidence in {@code [0, 1]} describing how
 * well the owner fits the file.
 *
 * @param owner      the owner token, e.g. {@code @api-team} or {@code alice@corp.com}
 * @param source     human-readable provenance, e.g. "already used in CODEOWNERS"
 * @param confidence fit score in {@code [0, 1]}; see {@link CodeownersOwnerSuggester}
 */
public record OwnerSuggestion(@NotNull String owner, @NotNull String source, double confidence) {

  /**
   * @return {@link #confidence} rounded to a whole percentage in {@code [0, 100]}.
   */
  public int confidencePercent() {
    return (int) Math.round(confidence * 100);
  }
}
