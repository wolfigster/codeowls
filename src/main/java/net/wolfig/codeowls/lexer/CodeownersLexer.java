package net.wolfig.codeowls.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handwritten lexer for CODEOWNERS files.
 *
 * <p>Each line is one of:
 * <ul>
 *   <li>Empty / whitespace-only</li>
 *   <li>Comment: {@code # text}</li>
 *   <li>GitLab section header: {@code [Name]} or {@code ^[Name]} (optional section),
 *       optionally followed by an approval count {@code [N]} and/or default owners
 *       on the same line (e.g. {@code [Backend][2] @org/backend @alice})</li>
 *   <li>Rule: {@code pattern  owner1 owner2 …}</li>
 * </ul>
 *
 * <p>Patterns may begin with {@code !} to negate (GitLab). Owners may be users
 * ({@code @alice}), teams ({@code @org/team}), roles ({@code @@maintainer}), or
 * e-mail addresses ({@code alice@example.com}).
 *
 * <p>State is kept across {@link #advance()} calls so the lexer knows whether it
 * is at the start of a line, reading owners, or just after a section header.
 */
public class CodeownersLexer extends LexerBase {

  // ---- states ----
  private static final int STATE_LINE_START = 0;     // beginning of any line
  private static final int STATE_OWNERS = 1;         // after the file-pattern token
  private static final int STATE_AFTER_SECTION = 2;  // after a [Section] token

  private CharSequence buffer;
  private int bufferEnd;
  private int tokenStart;
  private int tokenEnd;
  private IElementType tokenType;
  private int state;

  // ---- character predicates ----

  private static boolean isNewline(char c) {
    return c == '\n' || c == '\r';
  }

  private static boolean isHSpace(char c) {
    return c == ' ' || c == '\t';
  }

  private static boolean isWhitespace(char c) {
    return isHSpace(c) || isNewline(c);
  }

  private boolean peekIs(int offset, char expected) {
    return offset < bufferEnd && buffer.charAt(offset) == expected;
  }

  // ---- LexerBase contract ----

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.buffer = buffer;
    this.bufferEnd = endOffset;
    this.tokenEnd = startOffset;
    this.state = initialState;
    advance();
  }

  @Override
  public int getState() {
    return state;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return tokenType;
  }

  @Override
  public int getTokenStart() {
    return tokenStart;
  }

  @Override
  public int getTokenEnd() {
    return tokenEnd;
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return bufferEnd;
  }

  @Override
  public void advance() {
    tokenStart = tokenEnd;
    if (tokenStart >= bufferEnd) {
      tokenType = null;
      return;
    }
    char c = buffer.charAt(tokenStart);
    switch (state) {
      case STATE_OWNERS:
        lexOwners(c);
        break;
      case STATE_AFTER_SECTION:
        lexAfterSection(c);
        break;
      case STATE_LINE_START:
      default:
        lexLineStart(c);
        break;
    }
  }

  // ---- per-state lex methods ----

  private void lexLineStart(char c) {
    if (isNewline(c)) {
      consumeNewline();
      tokenType = TokenType.WHITE_SPACE;
      state = STATE_LINE_START;
    } else if (isHSpace(c)) {
      consumeHSpace();
      tokenType = TokenType.WHITE_SPACE;
      // state stays STATE_LINE_START — leading whitespace before the pattern
    } else if (c == '#') {
      consumeToEol();
      tokenType = CodeownersTokenTypes.COMMENT;
      // state stays STATE_LINE_START (EOL will reset it on next advance)
    } else if (c == '[' || (c == '^' && peekIs(tokenStart + 1, '['))) {
      // [Section] or ^[Section] (GitLab optional-section flag).
      // consumeBracketed scans up to the closing ']' regardless of the opening
      // character, so it handles both forms in one path.
      consumeBracketed();
      tokenType = CodeownersTokenTypes.SECTION_HEADER;
      state = STATE_AFTER_SECTION;
    } else {
      consumeNonSpace();
      tokenType = CodeownersTokenTypes.PATTERN;
      state = STATE_OWNERS;
    }
  }

  private void lexOwners(char c) {
    if (isNewline(c)) {
      consumeNewline();
      tokenType = TokenType.WHITE_SPACE;
      state = STATE_LINE_START;
    } else if (isHSpace(c)) {
      consumeHSpace();
      tokenType = TokenType.WHITE_SPACE;
    } else if (c == '@') {
      int start = tokenStart;
      consumeNonSpace();
      String token = buffer.subSequence(start, tokenEnd).toString();
      if (token.startsWith("@@")) {
        // @@maintainer / @@developer / @@developers (GitLab roles)
        tokenType = CodeownersTokenTypes.ROLE_OWNER;
      } else if (token.contains("/")) {
        // @org/team
        tokenType = CodeownersTokenTypes.TEAM_OWNER;
      } else {
        // @username
        tokenType = CodeownersTokenTypes.USER_OWNER;
      }
    } else {
      int start = tokenStart;
      consumeNonSpace();
      // email owner: contains '@' somewhere after position 0
      String token = buffer.subSequence(start, tokenEnd).toString();
      tokenType = token.indexOf('@') > 0 ? CodeownersTokenTypes.EMAIL_OWNER : CodeownersTokenTypes.BAD_CHARACTER;
    }
  }

  private void lexAfterSection(char c) {
    if (isNewline(c)) {
      consumeNewline();
      tokenType = TokenType.WHITE_SPACE;
      state = STATE_LINE_START;
    } else if (isHSpace(c)) {
      consumeHSpace();
      tokenType = TokenType.WHITE_SPACE;
    } else if (c == '[') {
      // Approval count [N] may appear once, directly after the section header.
      // Anything that follows is treated as default owners for the section.
      consumeBracketed();
      tokenType = CodeownersTokenTypes.APPROVAL_COUNT;
      state = STATE_OWNERS;
    } else {
      // GitLab allows default owners on the same line as the section header,
      // e.g. "[Backend][2] @org/backend @alice". Delegate to owner lexing.
      state = STATE_OWNERS;
      lexOwners(c);
    }
  }

  // ---- character-advance helpers ----

  private void consumeNewline() {
    tokenEnd = tokenStart + 1;
    // absorb \r\n as a single whitespace token
    if (buffer.charAt(tokenStart) == '\r' && tokenEnd < bufferEnd && buffer.charAt(tokenEnd) == '\n') {
      tokenEnd++;
    }
  }

  private void consumeHSpace() {
    tokenEnd = tokenStart + 1;
    while (tokenEnd < bufferEnd && isHSpace(buffer.charAt(tokenEnd))) tokenEnd++;
  }

  private void consumeToEol() {
    tokenEnd = tokenStart + 1;
    while (tokenEnd < bufferEnd && !isNewline(buffer.charAt(tokenEnd))) tokenEnd++;
  }

  private void consumeNonSpace() {
    tokenEnd = tokenStart + 1;
    while (tokenEnd < bufferEnd && !isWhitespace(buffer.charAt(tokenEnd))) tokenEnd++;
  }

  /**
   * Advances past {@code [...]} including both brackets. Stops at EOL if {@code ']'} is missing.
   */
  private void consumeBracketed() {
    tokenEnd = tokenStart + 1;
    while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != ']' && !isNewline(buffer.charAt(tokenEnd))) {
      tokenEnd++;
    }
    if (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) == ']') tokenEnd++;
  }
}
