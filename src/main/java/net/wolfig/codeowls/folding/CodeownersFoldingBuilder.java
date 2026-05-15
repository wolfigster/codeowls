package net.wolfig.codeowls.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds the body of each GitLab CODEOWNERS section.
 *
 * <p>Detection is line-based because {@link net.wolfig.codeowls.lang.CodeownersParserDefinition}
 * deliberately produces no AST: a section header is a line whose trimmed
 * content starts with {@code [} (optionally preceded by {@code ^} for GitLab
 * optional sections) and ends with {@code ]}. Each fold spans the header line
 * through the last consecutive non-blank line below it; a blank line (empty
 * or whitespace-only) or the next section header terminates the body, and
 * the terminating line is not part of the fold.
 */
public class CodeownersFoldingBuilder extends FoldingBuilderEx {

  private static @Nullable Header parseHeader(CharSequence text, int lineStart, int lineEnd, int line) {
    int contentStart = lineStart;
    while (contentStart < lineEnd && isHSpace(text.charAt(contentStart))) contentStart++;

    int contentEnd = lineEnd;
    while (contentEnd > contentStart && isHSpace(text.charAt(contentEnd - 1))) contentEnd--;

    if (contentEnd - contentStart < 2) return null;

    char first = text.charAt(contentStart);
    int bracketOpen;

    if (first == '[') {
      bracketOpen = contentStart;
    } else if (first == '^'
            && contentStart + 1 < contentEnd
            && text.charAt(contentStart + 1) == '[') {
      bracketOpen = contentStart + 1;
    } else {
      return null;
    }

    int bracketClose = findMatchingSectionClose(text, bracketOpen, contentEnd);
    if (bracketClose < 0) return null;

    // GitLab CODEOWNERS allows approval counts after the section name:
    // [Documentation][2]
    int headerEnd = bracketClose + 1;
    if (headerEnd < contentEnd && text.charAt(headerEnd) == '[') {
      int approvalClose = findClosingBracket(text, headerEnd, contentEnd);
      if (approvalClose < 0) return null;
      headerEnd = approvalClose + 1;
    }

    // After the section header / approval count only whitespace or owners may follow.
    // Example: [Documentation][2] @peter @org/team
    if (headerEnd < contentEnd && !isHSpace(text.charAt(headerEnd))) {
      return null;
    }

    String placeholder = text.subSequence(contentStart, headerEnd).toString();
    return new Header(line, lineStart, placeholder);
  }

  private static int findMatchingSectionClose(CharSequence text, int bracketOpen, int contentEnd) {
    return findClosingBracket(text, bracketOpen, contentEnd);
  }

  private static int findClosingBracket(CharSequence text, int bracketOpen, int contentEnd) {
    for (int i = bracketOpen + 1; i < contentEnd; i++) {
      if (text.charAt(i) == ']') {
        return i;
      }
    }
    return -1;
  }

  private static boolean isHSpace(char c) {
    return c == ' ' || c == '\t';
  }

  private static boolean isBlankLine(CharSequence text, int lineStart, int lineEnd) {
    for (int i = lineStart; i < lineEnd; i++) {
      if (!isHSpace(text.charAt(i))) return false;
    }
    return true;
  }

  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root,
                                                        @NotNull Document document,
                                                        boolean quick) {
    int lineCount = document.getLineCount();
    if (lineCount < 2) return FoldingDescriptor.EMPTY_ARRAY;

    ASTNode rootNode = root.getNode();
    if (rootNode == null) return FoldingDescriptor.EMPTY_ARRAY;

    CharSequence text = document.getCharsSequence();

    List<Header> headers = new ArrayList<>();
    for (int line = 0; line < lineCount; line++) {
      Header h = parseHeader(text,
              document.getLineStartOffset(line),
              document.getLineEndOffset(line),
              line);
      if (h != null) headers.add(h);
    }
    if (headers.isEmpty()) return FoldingDescriptor.EMPTY_ARRAY;

    List<FoldingDescriptor> descriptors = new ArrayList<>(headers.size());
    for (int i = 0; i < headers.size(); i++) {
      Header h = headers.get(i);
      int nextHeaderLine = (i + 1 < headers.size()) ? headers.get(i + 1).line() : lineCount;
      int endLine = h.line();
      for (int line = h.line() + 1; line < nextHeaderLine; line++) {
        int lineStart = document.getLineStartOffset(line);
        int lineEnd = document.getLineEndOffset(line);
        if (isBlankLine(text, lineStart, lineEnd)) break;
        endLine = line;
      }
      // Skip degenerate sections that have no body to hide.
      if (endLine <= h.line()) continue;
      int endOffset = document.getLineEndOffset(endLine);
      TextRange range = new TextRange(h.lineStartOffset(), endOffset);
      descriptors.add(new FoldingDescriptor(rootNode, range, null, h.placeholder()));
    }
    return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  @Override
  public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
    // Per-descriptor placeholder text is set in buildFoldRegions; this fallback
    // is used only if a descriptor was created without one.
    return "[...]";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  private record Header(int line, int lineStartOffset, String placeholder) {
  }
}
