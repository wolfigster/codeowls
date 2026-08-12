package net.wolfig.codeowls.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.*;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.OwnerToken;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.Plan;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.SectionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Refactor Owner" dialog: shows the owner under the caret, takes its
 * replacement, and lets the user pick how far the replacement reaches.
 *
 * <p>The scope radio buttons only appear when the owner actually sits inside a
 * GitLab section — outside a section a "current section" choice would be
 * meaningless, so the dialog states plainly that the whole file is affected.
 *
 * <p>Everything below the input is a live preview of the {@link Plan} that would
 * be executed: the occurrences it would touch (file:line plus the rule text) and
 * their count. Invalid input is reported through {@link #doValidate()}, which
 * also disables the Refactor button.
 */
public final class RefactorCodeownersOwnerDialog extends DialogWrapper {

  /**
   * Enough occurrences to judge the change by; longer lists are elided.
   */
  private static final int PREVIEW_LIMIT = 100;

  private final CharSequence content;
  private final OwnerToken owner;
  private final @Nullable SectionRange section;
  private final String fileName;

  private final JBTextField newOwnerField;
  private final JBRadioButton sectionScopeButton;
  private final JBRadioButton fileScopeButton;
  private final CollectionListModel<String> previewModel = new CollectionListModel<>();
  private final JBLabel summaryLabel = new JBLabel();

  public RefactorCodeownersOwnerDialog(@NotNull Project project,
                                       @NotNull CharSequence content,
                                       @NotNull OwnerToken owner,
                                       @Nullable SectionRange section,
                                       @NotNull String fileName) {
    super(project, true);
    this.content = content;
    this.owner = owner;
    this.section = section;
    this.fileName = fileName;

    this.newOwnerField = new JBTextField(owner.text(), 28);
    this.sectionScopeButton = new JBRadioButton(sectionScopeLabel(section), true);
    this.fileScopeButton = new JBRadioButton("Entire CODEOWNERS file", section == null);

    setTitle("Refactor CODEOWNERS Owner");
    setOKButtonText("Refactor");
    init();
    updatePreview();
  }

  private static @NotNull String sectionScopeLabel(@Nullable SectionRange section) {
    if (section == null) return "Current section";
    return section.name().isEmpty() ? "Current section" : "Current section [" + section.name() + "]";
  }

  /**
   * {@code "CODEOWNERS:4   /frontend/** @alice"}
   */
  private @NotNull String previewLine(@NotNull OwnerToken occurrence) {
    int line = CodeownersOwnerRefactoring.lineNumber(content, occurrence.startOffset()) + 1;
    return fileName + ":" + line + "   " + CodeownersOwnerRefactoring.lineText(content, occurrence.startOffset()).trim();
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    JBLabel currentOwnerLabel = new JBLabel(owner.text());
    currentOwnerLabel.setFont(JBFont.label().asBold());

    newOwnerField.selectAll();
    newOwnerField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updatePreview();
      }
    });

    JBList<String> previewList = new JBList<>(previewModel);
    previewList.getEmptyText().setText("No occurrences");
    previewList.setFocusable(false);
    previewList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JBScrollPane previewScroll = new JBScrollPane(previewList);
    previewScroll.setPreferredSize(JBUI.size(460, 120));

    FormBuilder builder = FormBuilder.createFormBuilder()
            .addLabeledComponent(new JBLabel("Current owner:"), currentOwnerLabel)
            .addLabeledComponent(new JBLabel("Replace with:"), newOwnerField)
            .addLabeledComponent(new JBLabel("Scope:"), createScopePanel())
            .addLabeledComponent(new JBLabel("Occurrences:"), previewScroll, 0, true)
            .addComponentToRightColumn(summaryLabel);

    JPanel panel = builder.getPanel();
    panel.setBorder(JBUI.Borders.emptyTop(4));
    return panel;
  }

  /**
   * The scope chooser — radio buttons when the owner is inside a section, plain
   * text when it is not and only a file-wide replacement is possible.
   */
  private @NotNull JComponent createScopePanel() {
    if (section == null) {
      return new JBLabel("Entire CODEOWNERS file");
    }
    ButtonGroup group = new ButtonGroup();
    group.add(sectionScopeButton);
    group.add(fileScopeButton);

    ItemListener listener = event -> updatePreview();
    sectionScopeButton.addItemListener(listener);
    fileScopeButton.addItemListener(listener);

    JPanel panel = new JPanel(new GridLayout(2, 1, 0, JBUI.scale(2)));
    panel.add(sectionScopeButton);
    panel.add(fileScopeButton);
    return panel;
  }

  private void updatePreview() {
    String newOwner = newOwner();
    if (!CodeownersOwnerRefactoring.isValidOwner(newOwner)) {
      previewModel.replaceAll(List.of());
      summaryLabel.setText(" ");
      return;
    }

    Plan plan = plan();
    List<String> lines = new ArrayList<>();
    for (OwnerToken occurrence : plan.occurrences()) {
      if (lines.size() == PREVIEW_LIMIT) {
        lines.add("… and " + (plan.occurrenceCount() - PREVIEW_LIMIT) + " more");
        break;
      }
      lines.add(previewLine(occurrence));
    }
    previewModel.replaceAll(lines);
    summaryLabel.setText(summary(plan));
  }

  private @NotNull String summary(@NotNull Plan plan) {
    if (plan.isNoOp()) {
      return plan.occurrenceCount() == 0
              ? "No occurrences in this scope."
              : "Nothing to change — that is the current owner.";
    }
    return plan.occurrenceCount() == 1
            ? "1 occurrence will be replaced."
            : plan.occurrenceCount() + " occurrences will be replaced.";
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    String newOwner = newOwner();
    if (newOwner.isEmpty()) {
      return new ValidationInfo("Enter a replacement owner", newOwnerField);
    }
    if (!CodeownersOwnerRefactoring.isValidOwner(newOwner)) {
      return new ValidationInfo(
              "'" + newOwner + "' is not a valid CODEOWNERS owner — expected a user (@alice), "
                      + "team (@org/team), role (@@maintainer), or e-mail (alice@example.com)",
              newOwnerField);
    }
    return null;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return newOwnerField;
  }

  /**
   * @return the replacement owner as typed, trimmed of surrounding whitespace.
   */
  public @NotNull String newOwner() {
    return newOwnerField.getText().trim();
  }

  public @NotNull OwnerRefactoringScope scope() {
    return section != null && sectionScopeButton.isSelected()
            ? OwnerRefactoringScope.SECTION
            : OwnerRefactoringScope.FILE;
  }

  /**
   * The refactoring the dialog currently describes.
   */
  public @NotNull Plan plan() {
    return CodeownersOwnerRefactoring.plan(content, owner, section, scope(), newOwner());
  }
}
