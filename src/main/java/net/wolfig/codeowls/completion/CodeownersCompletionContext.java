package net.wolfig.codeowls.completion;

import org.jetbrains.annotations.NotNull;

/**
 * Decides whether the caret in a CODEOWNERS file is currently in a pattern
 * segment, an owner segment, the name of a section header, a comment, or
 * somewhere completion shouldn't fire at all.
 *
 * <p>The decision is purely line-text-based — we scan the characters from the
 * start of the current line up to the caret. That's enough to be correct
 * (CODEOWNERS rules don't span multiple lines) and avoids depending on the
 * intermediate "dummy identifier" PSI state that completion otherwise inserts.
 *
 * <p>Section header lines have two completion-relevant positions: inside the
 * primary {@code [Name]} brackets (where we want to suggest section names) and
 * after the closing bracket(s) (where GitLab allows default owners — handled
 * by the regular owner provider, so we report {@link Segment#OWNER} there).
 */
public final class CodeownersCompletionContext {

  private final Segment segment;
  private final String typedSegmentText;

  private CodeownersCompletionContext(@NotNull Segment segment, @NotNull String typedSegmentText) {
    this.segment = segment;
    this.typedSegmentText = typedSegmentText;
  }

  /**
   * Analyse the prefix of the current line ending at the caret offset.
   *
   * @param lineBeforeCaret characters from the start of the line up to (but
   *                        not including) the caret offset
   */
  public static @NotNull CodeownersCompletionContext fromLinePrefix(@NotNull CharSequence lineBeforeCaret) {
    int n = lineBeforeCaret.length();
    int i = skipHSpace(lineBeforeCaret, 0, n);

    if (i == n) {
      // Empty or whitespace-only line — caret position is where a new pattern starts.
      return new CodeownersCompletionContext(Segment.PATTERN, "");
    }

    char first = lineBeforeCaret.charAt(i);
    if (first == '#') {
      return new CodeownersCompletionContext(Segment.COMMENT, "");
    }

    int sectionBracketOpen = sectionBracketOpenAt(lineBeforeCaret, i, n);
    if (sectionBracketOpen >= 0) {
      return analyzeSectionHeader(lineBeforeCaret, sectionBracketOpen, n);
    }

    return analyzeRuleLine(lineBeforeCaret, i, n);
  }

  /**
   * @return the offset of the first {@code [} of a section header (handling
   * the optional {@code ^} GitLab prefix), or -1 if the line doesn't start one.
   */
  private static int sectionBracketOpenAt(@NotNull CharSequence text, int i, int n) {
    if (text.charAt(i) == '[') return i;
    if (text.charAt(i) == '^' && i + 1 < n && text.charAt(i + 1) == '[') return i + 1;
    return -1;
  }

  /**
   * Caret is on a section header line; figure out where exactly.
   * <ul>
   *   <li>Inside the first {@code [...]} → section name completion.</li>
   *   <li>Inside a following approval-count {@code [N]} → neutral.</li>
   *   <li>After all brackets with whitespace before the caret → owner completion.</li>
   *   <li>Right after {@code ]} with no whitespace yet → neutral.</li>
   * </ul>
   */
  private static @NotNull CodeownersCompletionContext analyzeSectionHeader(
          @NotNull CharSequence text, int firstBracketOpen, int n) {
    int firstClose = indexOfChar(text, firstBracketOpen + 1, n, ']');
    if (firstClose < 0) {
      String typed = text.subSequence(firstBracketOpen + 1, n).toString();
      return new CodeownersCompletionContext(Segment.SECTION_HEADER_NAME, typed);
    }
    int after = firstClose + 1;
    if (after < n && text.charAt(after) == '[') {
      int approvalClose = indexOfChar(text, after + 1, n, ']');
      if (approvalClose < 0) {
        // Caret sits inside the approval-count brackets — nothing useful to offer.
        return new CodeownersCompletionContext(Segment.NONE, "");
      }
      after = approvalClose + 1;
    }
    return analyzeOwnerArea(text, after, n);
  }

  /**
   * Regular rule line — first whitespace separates pattern from owner segment.
   */
  private static @NotNull CodeownersCompletionContext analyzeRuleLine(
          @NotNull CharSequence text, int start, int n) {
    int patternEnd = start;
    while (patternEnd < n && !isHSpace(text.charAt(patternEnd))) patternEnd++;
    if (patternEnd >= n) {
      String typed = text.subSequence(start, n).toString();
      return new CodeownersCompletionContext(Segment.PATTERN, typed);
    }
    return analyzeOwnerArea(text, patternEnd, n);
  }

  /**
   * Common owner-segment analysis. Requires at least one whitespace between
   * {@code afterEnd} and the caret — otherwise the caret is wedged against
   * the previous token and offering owner suggestions there would emit
   * invalid syntax (e.g. {@code [Backend]@owner}).
   */
  private static @NotNull CodeownersCompletionContext analyzeOwnerArea(
          @NotNull CharSequence text, int afterEnd, int n) {
    int j = afterEnd;
    int whitespaceConsumed = 0;
    while (j < n && isHSpace(text.charAt(j))) {
      j++;
      whitespaceConsumed++;
    }
    if (whitespaceConsumed == 0) {
      // No whitespace gap before the caret — not yet in the owner segment.
      return new CodeownersCompletionContext(Segment.NONE, "");
    }
    int ownerStart = j;
    for (int k = j; k < n; k++) {
      if (isHSpace(text.charAt(k))) ownerStart = k + 1;
    }
    String typedOwner = ownerStart >= n ? "" : text.subSequence(ownerStart, n).toString();
    return new CodeownersCompletionContext(Segment.OWNER, typedOwner);
  }

  private static int skipHSpace(@NotNull CharSequence text, int from, int to) {
    int i = from;
    while (i < to && isHSpace(text.charAt(i))) i++;
    return i;
  }

  private static int indexOfChar(@NotNull CharSequence text, int from, int to, char target) {
    for (int i = from; i < to; i++) {
      if (text.charAt(i) == target) return i;
    }
    return -1;
  }

  private static boolean isHSpace(char c) {
    return c == ' ' || c == '\t';
  }

  /**
   * @return the segment the caret is in.
   */
  public @NotNull Segment segment() {
    return segment;
  }

  /**
   * @return the text typed so far within the current segment (the partial
   * pattern, the partial owner token, or the partial section name). Empty for
   * comments, neutral positions, or when the caret sits at the start of an
   * otherwise empty position.
   */
  public @NotNull String typedSegmentText() {
    return typedSegmentText;
  }

  public enum Segment {
    /**
     * Caret is in the pattern part — first whitespace-delimited token on the line.
     */
    PATTERN,
    /**
     * Caret is in an owner position: after a rule pattern OR after a section header's bracket(s).
     */
    OWNER,
    /**
     * Caret is inside a {@code #} comment; no completion.
     */
    COMMENT,
    /**
     * Caret is inside the name brackets of a section header ({@code [Backend]}).
     */
    SECTION_HEADER_NAME,
    /**
     * Caret is somewhere completion shouldn't fire (e.g. inside an approval count or a stray bracket).
     */
    NONE,
  }
}
