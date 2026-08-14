package net.wolfig.codeowls.entry;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import net.wolfig.codeowls.entry.CodeownersEntryRule.PathMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemListener;

/**
 * Native dialog for entering owners and previewing the generated rule.
 */
public final class AddCodeownersEntryDialog extends DialogWrapper {

  private final String relativePath;
  private final String exactPattern;
  private final String fileNamePattern;
  private final JBTextField ownersField = new JBTextField(32);
  private final JBRadioButton exactPathButton;
  private final JBRadioButton fileNameButton;
  private final JBTextArea preview = new JBTextArea();

  public AddCodeownersEntryDialog(@NotNull Project project, @NotNull String relativePath) {
    super(project, true);
    this.relativePath = relativePath.replace('\\', '/');
    this.exactPattern = CodeownersEntryRule.pattern(relativePath, PathMode.EXACT);
    this.fileNamePattern = CodeownersEntryRule.pattern(relativePath, PathMode.FILE_NAME);
    this.exactPathButton = new JBRadioButton(
            "Exact path from repository root (recommended)", true);
    this.fileNameButton = new JBRadioButton("File name only", false);

    setTitle("Add CODEOWNERS Entry");
    setOKButtonText("Add");
    init();
    updatePreview();
  }

  private static @NotNull JBLabel indentedLabel(@NotNull String text) {
    JBLabel label = new JBLabel(text);
    label.setBorder(JBUI.Borders.emptyLeft(22));
    return label;
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    ownersField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updatePreview();
      }
    });

    ButtonGroup paths = new ButtonGroup();
    paths.add(exactPathButton);
    paths.add(fileNameButton);
    ItemListener pathListener = event -> updatePreview();
    exactPathButton.addItemListener(pathListener);
    fileNameButton.addItemListener(pathListener);

    preview.setEditable(false);
    preview.setLineWrap(false);
    preview.setRows(2);
    preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, preview.getFont().getSize()));
    preview.setBorder(JBUI.Borders.empty(4));

    JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(new JBLabel("File:"), new JBLabel(relativePath))
            .addLabeledComponent(new JBLabel("Owners:"), ownersField)
            .addLabeledComponent(new JBLabel("Path:"), pathPanel())
            .addLabeledComponent(new JBLabel("Preview:"), preview, 0, true)
            .getPanel();
    panel.setBorder(JBUI.Borders.emptyTop(4));
    return panel;
  }

  private @NotNull JComponent pathPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(exactPathButton);
    panel.add(indentedLabel(exactPattern));
    panel.add(Box.createVerticalStrut(JBUI.scale(6)));
    panel.add(fileNameButton);
    panel.add(indentedLabel(fileNamePattern));
    JBLabel warning = indentedLabel(
            "File-name-only rules may match files in other directories.");
    warning.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    panel.add(warning);
    return panel;
  }

  private void updatePreview() {
    String rule = rule();
    preview.setText(rule != null ? rule : selectedPattern());
    setOKActionEnabled(rule != null);
  }

  private @NotNull String selectedPattern() {
    return fileNameButton.isSelected() ? fileNamePattern : exactPattern;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    String owners = ownersField.getText();
    if (CodeownersEntryRule.normalizeOwners(owners).isEmpty()) {
      return new ValidationInfo("Enter at least one owner", ownersField);
    }
    if (rule() == null) {
      return new ValidationInfo(
              "Enter valid CODEOWNERS owners, separated by spaces", ownersField);
    }
    return null;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return ownersField;
  }

  public @NotNull PathMode pathMode() {
    return fileNameButton.isSelected() ? PathMode.FILE_NAME : PathMode.EXACT;
  }

  public @Nullable String rule() {
    return CodeownersEntryRule.build(selectedPattern(), ownersField.getText());
  }

  public @NotNull String exactPattern() {
    return exactPattern;
  }
}
