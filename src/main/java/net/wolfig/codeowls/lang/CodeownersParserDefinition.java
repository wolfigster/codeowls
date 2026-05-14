package net.wolfig.codeowls.lang;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import net.wolfig.codeowls.lexer.CodeownersLexer;
import net.wolfig.codeowls.lexer.CodeownersTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal parser definition for CODEOWNERS.
 *
 * <p>The parser does not build any meaningful AST structure — it wraps the
 * entire file content in a single root marker. This is sufficient to enable
 * the Language/PSI infrastructure (and therefore syntax highlighting) without
 * requiring a grammar file or generated parser.
 *
 * <p>A future annotator, inspection, or completion contributor can be added
 * without changing this definition by inspecting lexer tokens directly.
 */
public class CodeownersParserDefinition implements ParserDefinition {

  public static final IFileElementType FILE =
          new IFileElementType(CodeownersLanguage.INSTANCE);

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new CodeownersLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return (root, builder) -> {
      PsiBuilder.Marker marker = builder.mark();
      while (!builder.eof()) {
        builder.advanceLexer();
      }
      marker.done(root);
      return builder.getTreeBuilt();
    };
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return TokenSet.WHITE_SPACE;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.create(CodeownersTokenTypes.COMMENT);
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    throw new AssertionError("No custom AST elements in CODEOWNERS: " + node.getElementType());
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PsiFileBase(viewProvider, CodeownersLanguage.INSTANCE) {
      @Override
      public @NotNull FileType getFileType() {
        return CodeownersFileType.INSTANCE;
      }
    };
  }
}
