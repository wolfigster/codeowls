package net.wolfig.codeowls.matcher;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import net.wolfig.codeowls.lexer.CodeownersLexer;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracts {@link CodeownersRule}s from CODEOWNERS file content by driving the
 * shared {@link CodeownersLexer} and grouping pattern + owner tokens on the
 * same line.
 *
 * <p>This is the only place where lexer tokens are translated into the rule
 * model — keeping it here avoids duplicating the lexer's awareness of token
 * categories elsewhere.
 *
 * <p>GitLab section headers and per-section default owners are intentionally
 * ignored. Effective ownership is decided by last-match-wins on rule lines
 * alone, which is correct for the great majority of real CODEOWNERS files.
 */
public final class CodeownersRuleParser {

  private CodeownersRuleParser() {
  }

  /**
   * Parse {@code content}. {@code source} is attached to each rule for navigation.
   */
  public static @NotNull List<CodeownersRule> parse(@NotNull CharSequence content,
                                                    @Nullable VirtualFile source) {
    if (content.isEmpty()) return Collections.emptyList();

    CodeownersLexer lexer = new CodeownersLexer();
    lexer.start(content, 0, content.length(), 0);

    List<CodeownersRule> rules = new ArrayList<>();
    String pendingPattern = null;
    int pendingLine = -1;
    List<String> pendingOwners = null;

    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();

      if (type == CodeownersTokenTypes.PATTERN) {
        if (pendingPattern != null) {
          rules.add(buildRule(pendingPattern, pendingOwners, source, pendingLine));
        }
        pendingPattern = content.subSequence(start, end).toString();
        pendingLine = lineNumberOf(content, start);
        pendingOwners = new ArrayList<>();
      } else if (pendingPattern != null && isOwnerToken(type)) {
        pendingOwners.add(content.subSequence(start, end).toString());
      } else if (type == TokenType.WHITE_SPACE && pendingPattern != null
              && containsNewline(content, start, end)) {
        rules.add(buildRule(pendingPattern, pendingOwners, source, pendingLine));
        pendingPattern = null;
        pendingOwners = null;
      }

      lexer.advance();
    }
    if (pendingPattern != null) {
      rules.add(buildRule(pendingPattern, pendingOwners, source, pendingLine));
    }
    return rules;
  }

  private static @NotNull CodeownersRule buildRule(@NotNull String pattern,
                                                   @NotNull List<String> owners,
                                                   @Nullable VirtualFile source,
                                                   int lineNumber) {
    return new CodeownersRule(
            pattern,
            List.copyOf(owners),
            CodeownersGlob.compile(pattern),
            source,
            lineNumber);
  }

  private static boolean isOwnerToken(IElementType type) {
    return type == CodeownersTokenTypes.USER_OWNER
            || type == CodeownersTokenTypes.TEAM_OWNER
            || type == CodeownersTokenTypes.ROLE_OWNER
            || type == CodeownersTokenTypes.EMAIL_OWNER;
  }

  private static boolean containsNewline(CharSequence content, int start, int end) {
    for (int i = start; i < end; i++) {
      char c = content.charAt(i);
      if (c == '\n' || c == '\r') return true;
    }
    return false;
  }

  private static int lineNumberOf(CharSequence content, int offset) {
    int line = 0;
    for (int i = 0; i < offset && i < content.length(); i++) {
      if (content.charAt(i) == '\n') line++;
    }
    return line;
  }
}
