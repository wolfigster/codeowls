package net.wolfig.codeowls.refactoring;

import com.intellij.psi.tree.IElementType;
import net.wolfig.codeowls.lexer.CodeownersLexer;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import net.wolfig.codeowls.matcher.CodeownersRuleParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The semantic model behind "Refactor Owner": which owner sits at a given
 * offset, which GitLab section encloses it, which other occurrences of that
 * owner exist, and the exact text edits that replace them.
 *
 * <p>Occurrences are never found by text search. The shared
 * {@link CodeownersLexer} is run over the file and only tokens it classifies as
 * owners ({@link CodeownersRuleParser#isOwnerToken}) are considered — the same
 * tokens that become the PSI leaves. Comments, glob patterns, section names and
 * approval counts are therefore structurally out of reach, and an owner matches
 * only on <em>exact</em> token text, so {@code @alice} never touches
 * {@code @alice-team}, {@code @alice2} or {@code alice@example.com}.
 *
 * <p>Edits are minimal text-range replacements, so all surrounding whitespace,
 * comments, line endings and unrelated rules survive untouched. Columns are
 * <em>not</em> re-aligned when the new owner has a different length.
 *
 * <p>One consequence of deferring to the lexer: it recognizes whole-line
 * comments only. On a rule line, {@code #} is a bad character and an
 * {@code @name} behind it is an owner as far as the whole plugin is concerned —
 * highlighted as one, attributed to the rule by {@link CodeownersRuleParser} —
 * so the refactoring rewrites it too. Whole-line comments are never touched.
 *
 * <p>Pure logic with no IntelliJ UI/PSI dependencies, so it is unit-tested
 * directly; {@link CodeownersOwnerRefactoringCommand} applies a {@link Plan} to
 * a real document.
 */
public final class CodeownersOwnerRefactoring {

  private CodeownersOwnerRefactoring() {
  }

  // ---- token / section discovery ----

  /**
   * Every owner token in {@code content}, in file order.
   */
  public static @NotNull List<OwnerToken> ownerTokens(@NotNull CharSequence content) {
    if (content.isEmpty()) return List.of();

    CodeownersLexer lexer = new CodeownersLexer();
    lexer.start(content, 0, content.length(), 0);

    List<OwnerToken> tokens = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      if (CodeownersRuleParser.isOwnerToken(type)) {
        int start = lexer.getTokenStart();
        int end = lexer.getTokenEnd();
        tokens.add(new OwnerToken(content.subSequence(start, end).toString(), start, end, type));
      }
      lexer.advance();
    }
    return tokens;
  }

  /**
   * The owner token covering {@code offset}, or {@code null} when the offset is
   * on anything else (pattern, comment, section name, approval count,
   * whitespace, …).
   *
   * <p>The end offset counts as inside, so a caret parked immediately after
   * {@code @alice} still resolves to it. Owner tokens are always whitespace
   * separated, so that can never claim a neighboring token.
   */
  public static @Nullable OwnerToken ownerTokenAt(@NotNull CharSequence content, int offset) {
    for (OwnerToken token : ownerTokens(content)) {
      if (offset >= token.startOffset() && offset <= token.endOffset()) return token;
    }
    return null;
  }

  /**
   * Every GitLab section in {@code content}, in file order. A section runs from
   * its header token (inclusive — so default owners declared on the header line
   * belong to it) up to the next header, or to EOF for the last one.
   */
  public static @NotNull List<SectionRange> sections(@NotNull CharSequence content) {
    if (content.isEmpty()) return List.of();

    CodeownersLexer lexer = new CodeownersLexer();
    lexer.start(content, 0, content.length(), 0);

    List<String> names = new ArrayList<>();
    List<Integer> starts = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == CodeownersTokenTypes.SECTION_HEADER) {
        int start = lexer.getTokenStart();
        names.add(CodeownersRuleParser.sectionName(
                content.subSequence(start, lexer.getTokenEnd()).toString()));
        starts.add(start);
      }
      lexer.advance();
    }

    List<SectionRange> sections = new ArrayList<>(starts.size());
    for (int i = 0; i < starts.size(); i++) {
      int end = i + 1 < starts.size() ? starts.get(i + 1) : content.length();
      sections.add(new SectionRange(names.get(i), starts.get(i), end));
    }
    return sections;
  }

  /**
   * The section enclosing {@code offset}, or {@code null} when the offset lies
   * before the first section header — i.e. the owner is not in any section and
   * only a file-wide replacement makes sense.
   */
  public static @Nullable SectionRange sectionAt(@NotNull CharSequence content, int offset) {
    for (SectionRange section : sections(content)) {
      if (offset >= section.startOffset() && offset < section.endOffset()) return section;
    }
    return null;
  }

  // ---- validation ----

  /**
   * Whether {@code candidate} is something the CODEOWNERS lexer would read back
   * as a single owner — a user ({@code @alice}), a team ({@code @org/team}), a
   * GitLab role ({@code @@maintainer}) or an e-mail ({@code alice@example.com}).
   *
   * <p>Decided by lexing {@code candidate} in owner position rather than by a
   * separate rule set, so validation can never drift from the parser: anything
   * the lexer would flag as a bad character ({@code hello}), or split into more
   * than one token ({@code hello world}), is rejected.
   */
  public static boolean isValidOwner(@Nullable String candidate) {
    if (candidate == null || candidate.isEmpty()) return false;

    // "*" puts the lexer past the pattern and into owner position; the owner
    // then starts at a known offset and must span the rest of the probe.
    String probe = "* " + candidate;
    List<OwnerToken> tokens = ownerTokens(probe);
    return tokens.size() == 1
            && tokens.getFirst().startOffset() == 2
            && tokens.getFirst().endOffset() == probe.length();
  }

  // ---- planning ----

  /**
   * Plans the replacement of the selected owner within {@code scope}.
   */
  public static @NotNull Plan plan(@NotNull CharSequence content,
                                   @NotNull OwnerToken selected,
                                   @Nullable SectionRange section,
                                   @NotNull OwnerRefactoringScope scope,
                                   @NotNull String newOwner) {
    boolean inSection = scope == OwnerRefactoringScope.SECTION && section != null;
    int from = inSection ? section.startOffset() : 0;
    int to = inSection ? section.endOffset() : content.length();
    return plan(content, selected.text(), newOwner, from, to);
  }

  /**
   * Plans the replacement of every {@code currentOwner} occurrence whose token
   * starts within {@code [rangeStart, rangeEnd)}.
   *
   * <p>Replacing an owner with itself, or replacing an owner that does not occur
   * in the range, yields a plan with no edits — the refactoring then leaves the
   * file byte-identical.
   *
   * <p>On every line the refactoring touches, an owner that would become a
   * duplicate of one already present is dropped (together with the whitespace in
   * front of it) instead of being written twice: {@code /api/** @alice @bob}
   * with {@code @alice → @bob} becomes {@code /api/** @bob}. Duplicates that
   * already existed on the line and are unrelated to the replacement are left
   * exactly as they are.
   */
  public static @NotNull Plan plan(@NotNull CharSequence content,
                                   @NotNull String currentOwner,
                                   @NotNull String newOwner,
                                   int rangeStart,
                                   int rangeEnd) {
    List<OwnerToken> all = ownerTokens(content);

    List<OwnerToken> occurrences = new ArrayList<>();
    Set<Integer> occurrenceStarts = new LinkedHashSet<>();
    for (OwnerToken token : all) {
      if (token.startOffset() >= rangeStart && token.startOffset() < rangeEnd
              && token.text().equals(currentOwner)) {
        occurrences.add(token);
        occurrenceStarts.add(token.startOffset());
      }
    }
    if (occurrences.isEmpty() || currentOwner.equals(newOwner)) {
      return new Plan(currentOwner, newOwner, List.copyOf(occurrences), List.of());
    }

    // Owners are line-scoped: a rule's owners, or a section header's default
    // owners, all live on one line. De-duplication therefore reasons per line.
    // Tokens come out in file order, so one walk of the text numbers them all.
    Map<Integer, List<OwnerToken>> byLine = new LinkedHashMap<>();
    int line = 0;
    int cursor = 0;
    for (OwnerToken token : all) {
      while (cursor < token.startOffset()) {
        if (content.charAt(cursor) == '\n') line++;
        cursor++;
      }
      byLine.computeIfAbsent(line, key -> new ArrayList<>()).add(token);
    }

    List<Edit> edits = new ArrayList<>();
    for (List<OwnerToken> lineTokens : byLine.values()) {
      boolean touched = lineTokens.stream().anyMatch(t -> occurrenceStarts.contains(t.startOffset()));
      if (!touched) continue;

      // Owner value on this line -> was it introduced by the refactoring?
      Map<String, Boolean> seen = new LinkedHashMap<>();
      for (OwnerToken token : lineTokens) {
        boolean replaced = occurrenceStarts.contains(token.startOffset());
        String value = replaced ? newOwner : token.text();
        Boolean introducedByRefactoring = seen.get(value);
        if (introducedByRefactoring == null) {
          seen.put(value, replaced);
          if (replaced) edits.add(new Edit(token.startOffset(), token.endOffset(), newOwner));
        } else if (replaced || introducedByRefactoring) {
          // The duplicate exists only because of this refactoring — drop it.
          edits.add(deletion(content, token));
        }
        // else: a duplicate the file already had; not ours to clean up.
      }
    }
    edits.sort((a, b) -> Integer.compare(a.startOffset(), b.startOffset()));
    return new Plan(currentOwner, newOwner, List.copyOf(occurrences), List.copyOf(edits));
  }

  /**
   * Removes an owner token together with the whitespace that separates it from
   * the previous token, so the remaining line keeps its shape. Falls back to
   * absorbing trailing whitespace when the token starts its line.
   */
  private static @NotNull Edit deletion(@NotNull CharSequence content, @NotNull OwnerToken token) {
    int lineStart = lineStartOffset(content, token.startOffset());
    int from = token.startOffset();
    while (from > lineStart && isHSpace(content.charAt(from - 1))) from--;
    if (from < token.startOffset()) return new Edit(from, token.endOffset(), "");

    int to = token.endOffset();
    while (to < content.length() && isHSpace(content.charAt(to))) to++;
    return new Edit(token.startOffset(), to, "");
  }

  /**
   * Applies {@code edits} to {@code content} and returns the result. Used by the
   * tests and by anything that needs the outcome without a document; the real
   * refactoring edits the document in place (see
   * {@link CodeownersOwnerRefactoringCommand}).
   */
  public static @NotNull String applyEdits(@NotNull CharSequence content, @NotNull List<Edit> edits) {
    StringBuilder sb = new StringBuilder(content);
    // Back to front, so earlier offsets stay valid.
    for (int i = edits.size() - 1; i >= 0; i--) {
      Edit edit = edits.get(i);
      sb.replace(edit.startOffset(), edit.endOffset(), edit.replacement());
    }
    return sb.toString();
  }

  // ---- line helpers ----

  /**
   * 0-based line index of {@code offset}.
   */
  public static int lineNumber(@NotNull CharSequence content, int offset) {
    int line = 0;
    for (int i = 0; i < offset && i < content.length(); i++) {
      if (content.charAt(i) == '\n') line++;
    }
    return line;
  }

  /**
   * The full text of the line containing {@code offset}, without its line break.
   */
  public static @NotNull String lineText(@NotNull CharSequence content, int offset) {
    int start = lineStartOffset(content, offset);
    int end = start;
    while (end < content.length() && content.charAt(end) != '\n' && content.charAt(end) != '\r') end++;
    return content.subSequence(start, end).toString();
  }

  private static int lineStartOffset(@NotNull CharSequence content, int offset) {
    int i = Math.min(offset, content.length());
    while (i > 0 && content.charAt(i - 1) != '\n' && content.charAt(i - 1) != '\r') i--;
    return i;
  }

  private static boolean isHSpace(char c) {
    return c == ' ' || c == '\t';
  }

  /**
   * One owner as the lexer sees it: its exact text, where it sits, and which
   * owner category it belongs to.
   */
  public record OwnerToken(@NotNull String text, int startOffset, int endOffset, @NotNull IElementType type) {
  }

  /**
   * A GitLab section and the offsets it spans, header included.
   */
  public record SectionRange(@NotNull String name, int startOffset, int endOffset) {
  }

  /**
   * A single text-range replacement. An empty {@link #replacement()} deletes.
   */
  public record Edit(int startOffset, int endOffset, @NotNull String replacement) {
  }

  /**
   * What a refactoring would do: the occurrences it found (for the preview) and
   * the edits that carry it out, sorted by offset and non-overlapping.
   */
  public record Plan(@NotNull String currentOwner,
                     @NotNull String newOwner,
                     @NotNull List<OwnerToken> occurrences,
                     @NotNull List<Edit> edits) {

    public int occurrenceCount() {
      return occurrences.size();
    }

    /**
     * @return {@code true} when applying this plan would not change the file.
     */
    public boolean isNoOp() {
      return edits.isEmpty();
    }
  }
}
