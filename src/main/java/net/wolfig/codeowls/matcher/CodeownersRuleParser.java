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
 * <p>GitLab section headers may declare <em>default owners</em> on the header
 * line, e.g. {@code [Backend][2] @org/backend @alice}. Those owners apply to
 * every rule in the section that does not list owners of its own; a rule that
 * does list owners overrides the default entirely. A section's scope runs from
 * its header to the next header (or end of file), and a header with no owners
 * clears any previously inherited default. The owners stored on each
 * {@link CodeownersRule} are therefore the <em>effective</em> owners, after
 * inheritance has been applied.
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

    // Default owners declared on the current section header line, inherited by
    // rules in the section that name no owners of their own. Reset by each new
    // section header.
    List<String> sectionDefaultOwners = Collections.emptyList();
    // While true we are accumulating owners from a section header line into
    // headerOwners; they become the section default once the line ends.
    boolean inSectionHeader = false;
    List<String> headerOwners = null;

    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();

      if (type == CodeownersTokenTypes.SECTION_HEADER) {
        if (pendingPattern != null) {
          rules.add(buildRule(pendingPattern, effectiveOwners(pendingOwners, sectionDefaultOwners), source, pendingLine));
          pendingPattern = null;
          pendingOwners = null;
        }
        // Begin collecting the new section's default owners; the previous
        // section's default no longer applies once a header is seen.
        inSectionHeader = true;
        headerOwners = new ArrayList<>();
      } else if (type == CodeownersTokenTypes.PATTERN) {
        if (pendingPattern != null) {
          rules.add(buildRule(pendingPattern, effectiveOwners(pendingOwners, sectionDefaultOwners), source, pendingLine));
        }
        pendingPattern = content.subSequence(start, end).toString();
        pendingLine = lineNumberOf(content, start);
        pendingOwners = new ArrayList<>();
      } else if (isOwnerToken(type)) {
        if (inSectionHeader) {
          headerOwners.add(content.subSequence(start, end).toString());
        } else if (pendingPattern != null) {
          pendingOwners.add(content.subSequence(start, end).toString());
        }
      } else if (type == TokenType.WHITE_SPACE && containsNewline(content, start, end)) {
        if (inSectionHeader) {
          sectionDefaultOwners = List.copyOf(headerOwners);
          inSectionHeader = false;
          headerOwners = null;
        } else if (pendingPattern != null) {
          rules.add(buildRule(pendingPattern, effectiveOwners(pendingOwners, sectionDefaultOwners), source, pendingLine));
          pendingPattern = null;
          pendingOwners = null;
        }
      }

      lexer.advance();
    }
    if (pendingPattern != null) {
      rules.add(buildRule(pendingPattern, effectiveOwners(pendingOwners, sectionDefaultOwners), source, pendingLine));
    }
    return rules;
  }

  /**
   * A rule's effective owners: the owners it declares, or — when it declares
   * none — the default owners inherited from its enclosing section.
   */
  private static @NotNull List<String> effectiveOwners(@NotNull List<String> ruleOwners,
                                                       @NotNull List<String> sectionDefaultOwners) {
    return ruleOwners.isEmpty() ? sectionDefaultOwners : ruleOwners;
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
