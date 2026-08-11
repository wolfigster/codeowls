package net.wolfig.codeowls.explain;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import net.wolfig.codeowls.matcher.CodeownersSection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Native popup that explains the CODEOWNERS ownership of a file: its effective
 * owners and required approvals, every matching rule in evaluation order with
 * the winning one marked, and any GitLab section inheritance.
 *
 * <p>Each matching rule is selectable; pressing Enter or double-clicking it
 * navigates to the exact line in the governing CODEOWNERS file (reusing
 * {@link OpenFileDescriptor}, the same navigation the status-bar widget uses).
 *
 * <p>Purely presentational — it is handed an already-computed, immutable
 * {@link OwnershipExplanation} and does no matching of its own.
 */
public final class OwnershipExplanationPopup {

  private static final SimpleTextAttributes EFFECTIVE =
          new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, new JBColor(0x59A869, 0x499C54));

  private OwnershipExplanationPopup() {
  }

  public static void show(@NotNull Project project,
                          @NotNull OwnershipExplanation explanation,
                          @NotNull RelativePoint where) {
    JPanel root = new JPanel(new BorderLayout());
    root.setBorder(JBUI.Borders.empty(8));

    JBLabel header = new JBLabel(headerHtml(explanation));
    header.setVerticalAlignment(SwingConstants.TOP);
    root.add(header, BorderLayout.NORTH);

    JComponent focus = header;
    JBPopup[] popupRef = new JBPopup[1];

    if (explanation.hasMatch()) {
      JBList<MatchedRule> list = new JBList<>(explanation.matchedRules());
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setCellRenderer(new RuleRenderer());
      list.setSelectedIndex(explanation.matchedRules().size() - 1); // the effective rule
      list.setBorder(JBUI.Borders.emptyTop(4));

      Runnable navigate = () -> {
        MatchedRule selected = list.getSelectedValue();
        if (selected != null && navigateTo(project, selected)) {
          if (popupRef[0] != null) popupRef[0].closeOk(null);
        }
      };
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent event) {
          navigate.run();
          return true;
        }
      }.installOn(list);
      list.registerKeyboardAction(e -> navigate.run(),
              KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

      JBScrollPane scroll = new JBScrollPane(list);
      scroll.setBorder(JBUI.Borders.emptyTop(6));
      root.add(scroll, BorderLayout.CENTER);
      focus = list;
    }

    root.setPreferredSize(new Dimension(JBUI.scale(520),
            JBUI.scale(explanation.hasMatch() ? 260 : 130)));

    JBPopup popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(root, focus)
            .setTitle("CODEOWNERS Ownership")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .createPopup();
    popupRef[0] = popup;
    popup.show(where);
  }

  private static boolean navigateTo(@NotNull Project project, @NotNull MatchedRule rule) {
    VirtualFile source = rule.sourceFile();
    if (source == null || !source.isValid()) return false;
    new OpenFileDescriptor(project, source, rule.line(), 0).navigate(true);
    return true;
  }

  private static @NotNull String headerHtml(@NotNull OwnershipExplanation explanation) {
    StringBuilder sb = new StringBuilder("<html><body style='width:480px'>");
    sb.append("<b>").append(esc(displayPath(explanation))).append("</b><br><br>");

    if (!explanation.hasMatch()) {
      if (!explanation.hasCodeownersFile()) {
        sb.append("No CODEOWNERS file governs this file.<br><br>");
      }
      sb.append("No matching CODEOWNERS rule found.<br><br>");
      sb.append("This file currently has no CODEOWNERS owner.");
      return sb.append("</body></html>").toString();
    }

    List<String> owners = explanation.effectiveOwners();
    sb.append("<b>Effective owners:</b> ")
            .append(owners.isEmpty() ? "<i>none</i>" : esc(String.join(", ", owners)))
            .append("<br>");

    Integer approvals = explanation.effectiveApprovalCount();
    if (approvals != null) {
      sb.append("<b>Required approvals:</b> ").append(approvals).append("<br>");
    }

    MatchedRule effective = explanation.effectiveRule();
    if (effective != null && effective.inheritedFromSection() && effective.section() != null) {
      CodeownersSection section = effective.section();
      sb.append("<br><b>Inherited from section</b> ")
              .append(esc(section.displayHeader())).append(":<br>");
      sb.append("&nbsp;&nbsp;Owners: ")
              .append(esc(String.join(", ", section.defaultOwners()))).append("<br>");
      if (section.approvalCount() != null) {
        sb.append("&nbsp;&nbsp;Required approvals: ").append(section.approvalCount()).append("<br>");
      }
    }

    sb.append("<br><b>Matching rules</b> (evaluation order):");
    return sb.append("</body></html>").toString();
  }

  private static @NotNull String displayPath(@NotNull OwnershipExplanation explanation) {
    return explanation.relativePath() != null ? explanation.relativePath() : explanation.targetFile().getName();
  }

  private static @NotNull String esc(@NotNull String s) {
    return StringUtil.escapeXmlEntities(s);
  }

  /**
   * Renders one matching rule: {@code ✓ <pattern>  <owners>  ← effective rule},
   * with a trailing inheritance note when the owners came from a section.
   */
  private static final class RuleRenderer extends com.intellij.ui.ColoredListCellRenderer<MatchedRule> {
    private static String sourceTooltip(@NotNull MatchedRule value) {
      VirtualFile source = value.sourceFile();
      String where = source == null ? "unknown" : source.getName() + ":" + (value.line() + 1);
      return "Defined at " + where;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends MatchedRule> list,
                                         MatchedRule value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value == null) return;
      append("✓ ");
      append(value.pattern());
      List<String> owners = value.resolvedOwners();
      String ownerText = owners.isEmpty() ? "(no owner)" : String.join(" ", owners);
      append("    " + ownerText, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      if (value.effective()) {
        append("    ← effective rule", EFFECTIVE);
      }
      if (value.inheritedFromSection() && value.section() != null) {
        append("   [inherited from " + value.section().name() + "]",
                SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
      }
      setToolTipText(sourceTooltip(value));
    }
  }
}
