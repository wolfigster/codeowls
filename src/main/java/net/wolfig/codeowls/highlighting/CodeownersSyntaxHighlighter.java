package net.wolfig.codeowls.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import net.wolfig.codeowls.lexer.CodeownersLexer;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Maps each {@link CodeownersTokenTypes} token to an editor
 * {@link TextAttributesKey} from {@link CodeownersHighlightingColors}.
 */
public class CodeownersSyntaxHighlighter extends SyntaxHighlighterBase {

  private static final TextAttributesKey[] NO_KEYS = TextAttributesKey.EMPTY_ARRAY;

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new CodeownersLexer();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    if (tokenType == CodeownersTokenTypes.COMMENT) return pack(CodeownersHighlightingColors.COMMENT);
    if (tokenType == CodeownersTokenTypes.PATTERN) return pack(CodeownersHighlightingColors.PATTERN);
    if (tokenType == CodeownersTokenTypes.USER_OWNER) return pack(CodeownersHighlightingColors.USER_OWNER);
    if (tokenType == CodeownersTokenTypes.TEAM_OWNER) return pack(CodeownersHighlightingColors.TEAM_OWNER);
    if (tokenType == CodeownersTokenTypes.ROLE_OWNER) return pack(CodeownersHighlightingColors.ROLE_OWNER);
    if (tokenType == CodeownersTokenTypes.EMAIL_OWNER) return pack(CodeownersHighlightingColors.EMAIL_OWNER);
    if (tokenType == CodeownersTokenTypes.SECTION_HEADER) return pack(CodeownersHighlightingColors.SECTION_HEADER);
    if (tokenType == CodeownersTokenTypes.APPROVAL_COUNT) return pack(CodeownersHighlightingColors.APPROVAL_COUNT);
    if (tokenType == CodeownersTokenTypes.BAD_CHARACTER) return pack(CodeownersHighlightingColors.BAD_CHARACTER);
    return NO_KEYS;
  }
}
