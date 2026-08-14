package net.wolfig.codeowls.entry;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Applies a minimal CODEOWNERS rule edit as one undoable write command.
 */
public final class CodeownersEntryWriter {

  public static final String COMMAND_NAME = "Add CODEOWNERS Entry";

  private CodeownersEntryWriter() {
  }

  public static @NotNull String appendRuleText(@NotNull CharSequence content,
                                               @NotNull String rule) {
    String lineSeparator = lineSeparator(content);
    String prefix = content.isEmpty() || endsWithLineBreak(content) ? "" : lineSeparator;
    return content + prefix + rule + lineSeparator;
  }

  public static void appendAndNavigate(@NotNull Project project,
                                       @NotNull VirtualFile codeownersFile,
                                       @NotNull String rule) {
    applyAndNavigate(project, codeownersFile, rule, null);
  }

  public static void replaceAndNavigate(@NotNull Project project,
                                        @NotNull VirtualFile codeownersFile,
                                        @NotNull String rule,
                                        int lineNumber) {
    applyAndNavigate(project, codeownersFile, rule, lineNumber);
  }

  private static void applyAndNavigate(@NotNull Project project,
                                       @NotNull VirtualFile codeownersFile,
                                       @NotNull String rule,
                                       @Nullable Integer replacementLine) {
    if (!codeownersFile.isValid() || codeownersFile.isDirectory()) return;
    int[] ruleOffset = {-1};

    WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME, null, () -> {
      Document document = FileDocumentManager.getInstance().getDocument(codeownersFile);
      if (document == null) return;

      if (replacementLine != null
              && replacementLine >= 0
              && replacementLine < document.getLineCount()) {
        int start = document.getLineStartOffset(replacementLine);
        int end = document.getLineEndOffset(replacementLine);
        document.replaceString(start, end, rule);
        ruleOffset[0] = start;
      } else {
        CharSequence content = document.getCharsSequence();
        String lineSeparator = lineSeparator(content);
        String prefix = content.isEmpty() || endsWithLineBreak(content) ? "" : lineSeparator;
        ruleOffset[0] = document.getTextLength() + prefix.length();
        document.insertString(document.getTextLength(), prefix + rule + lineSeparator);
      }

      PsiDocumentManager.getInstance(project).commitDocument(document);
      FileDocumentManager.getInstance().saveDocument(document);
    });

    if (ruleOffset[0] >= 0) {
      new OpenFileDescriptor(project, codeownersFile, ruleOffset[0]).navigate(true);
    }
  }

  private static boolean endsWithLineBreak(@NotNull CharSequence content) {
    if (content.isEmpty()) return false;
    char last = content.charAt(content.length() - 1);
    return last == '\n' || last == '\r';
  }

  private static @NotNull String lineSeparator(@NotNull CharSequence content) {
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '\r') {
        return i + 1 < content.length() && content.charAt(i + 1) == '\n' ? "\r\n" : "\r";
      }
      if (c == '\n') return "\n";
    }
    return "\n";
  }
}
