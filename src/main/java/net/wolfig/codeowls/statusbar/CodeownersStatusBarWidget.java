package net.wolfig.codeowls.statusbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import net.wolfig.codeowls.matcher.CodeownersRule;
import net.wolfig.codeowls.suggestion.CodeownersSuggestionPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Status bar widget that shows the CODEOWNERS owners of the currently
 * selected file.
 *
 * <p>Re-resolution is driven by three event sources:
 * <ul>
 *   <li>{@link FileEditorManagerListener} — the selected file changed.</li>
 *   <li>{@link BulkFileListener} on VFS — the CODEOWNERS file (or the active
 *       file's path) changed on disk.</li>
 *   <li>{@link PsiTreeChangeAdapter} on {@link PsiManager} — an in-editor
 *       edit to a CODEOWNERS file (the VFS stamp does not change until save).</li>
 * </ul>
 *
 * <p>The work itself (computing the matching rule) is offloaded to a
 * non-blocking read action, so the UI thread never parses or matches.
 *
 * <p>Clicking the widget opens the CODEOWNERS file at the matching rule's
 * line; if no rule matches but a CODEOWNERS file exists, it opens the file
 * at the top.
 */
public final class CodeownersStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {

  private final Project project;
  private StatusBar statusBar;

  /**
   * Most recent resolution result. Written on UI thread, read by presentation getters.
   */
  private volatile CodeownersOwnerResolution resolution = CodeownersOwnerResolution.NONE;

  public CodeownersStatusBarWidget(@NotNull Project project) {
    this.project = project;
  }

  private static boolean isCodeowners(@Nullable PsiFile file) {
    return file != null && "CODEOWNERS".equals(file.getName());
  }

  @Override
  public @NotNull String ID() {
    return CodeownersStatusBarWidgetFactory.ID;
  }

  @Override
  public @NotNull WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    this.statusBar = statusBar;

    MessageBusConnection bus = project.getMessageBus().connect(this);
    bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        scheduleUpdate();
      }

      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        scheduleUpdate();
      }
    });
    bus.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent e : events) {
          String path = e.getPath();
          // Any rename, content change, create, or delete touching a file
          // named CODEOWNERS, or touching the currently-active file, warrants
          // a re-resolve.
          if (path.endsWith("/CODEOWNERS") || path.equals("CODEOWNERS")) {
            scheduleUpdate();
            return;
          }
        }
      }
    });

    // PSI changes catch unsaved edits to CODEOWNERS — VFS does not fire for those.
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        if (isCodeowners(event.getFile())) scheduleUpdate();
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        if (isCodeowners(event.getFile())) scheduleUpdate();
      }
    }, this);

    scheduleUpdate();
  }

  /**
   * Recompute the owners off the UI thread, then push the result back via
   * {@link StatusBar#updateWidget}. Cancellable via {@link Disposable#dispose()}.
   */
  private void scheduleUpdate() {
    if (project.isDisposed()) return;
    ReadAction.nonBlocking(() -> {
              VirtualFile current = currentFile();
              return CodeownersService.getInstance(project).resolveOwners(current);
            })
            .expireWith(this)
            .finishOnUiThread(ModalityState.any(), result -> {
              resolution = result;
              if (statusBar != null) statusBar.updateWidget(ID());
            })
            .submit(AppExecutorUtil.getAppExecutorService());
  }

  private @Nullable VirtualFile currentFile() {
    if (project.isDisposed()) return null;
    VirtualFile[] selected = FileEditorManager.getInstance(project).getSelectedFiles();
    return selected.length > 0 ? selected[0] : null;
  }

  @Override
  public void dispose() {
    // MessageBusConnection and PSI listener are tied to `this` and disposed by the platform.
    statusBar = null;
  }

  // ---- IconPresentation ----

  @Override
  public @NotNull Icon getIcon() {
    CodeownersRule rule = resolution.rule();
    // No specific owner (no match, no owners, or only the global wildcard):
    // surface the "access" glyph, matching the click → suggestion behaviour.
    if (needsSuggestions(rule)) return AllIcons.CodeWithMe.CwmAccess;
    return rule.owners().size() == 1 ? AllIcons.General.User : AllIcons.CodeWithMe.Users;
  }

  @Override
  public @NotNull String getTooltipText() {
    CodeownersOwnerResolution res = resolution;
    if (res.isEmpty()) return "No CODEOWNERS rule matches this file";
    CodeownersRule rule = res.rule();
    if (rule == null) return "No CODEOWNERS rule matches this file";
    String codeowners = !rule.owners().isEmpty() ?
            StringUtil.escapeXmlEntities(String.join(", ", rule.owners())) : "Unknown";
    return "<html>" +
            "<b>Owners:</b> " + codeowners + "<br>" +
            "<b>Pattern:</b> <em>" + StringUtil.escapeXmlEntities(rule.pattern()) + "</em><br>" +
            "<b>Source:</b> " + StringUtil.escapeXmlEntities(displaySourcePath(rule)) +
            "</html>";
  }

  @Override
  public @NotNull Consumer<MouseEvent> getClickConsumer() {
    return this::onClick;
  }

  /**
   * When the selected file has no specific owner, offers owner suggestions;
   * otherwise navigates to the matching rule. That is the moment the user most
   * needs help, so the click is repurposed to a suggestion popup there.
   */
  private void onClick(@NotNull MouseEvent e) {
    if (project.isDisposed()) return;
    if (needsSuggestions(resolution.rule())) {
      VirtualFile target = currentFile();
      if (target != null) {
        CodeownersSuggestionPopup.show(project, target, new RelativePoint(e));
        return;
      }
    }
    navigateToMatchedRule();
  }

  /**
   * A file "needs suggestions" when it has no specific owner: no rule matched,
   * the matched rule lists no owners, or it is owned only by the global wildcard
   * rule ({@code *} / {@code **}), which assigns a repo-wide default rather than
   * a real owner for this particular file.
   */
  static boolean needsSuggestions(@Nullable CodeownersRule rule) {
    if (rule == null || rule.owners().isEmpty()) return true;
    return isGlobalWildcard(rule.pattern());
  }

  private static boolean isGlobalWildcard(@NotNull String pattern) {
    String p = pattern.trim();
    return p.equals("*") || p.equals("**");
  }

  /**
   * Opens the active CODEOWNERS file at the matched rule's line. If no rule
   * matches but a CODEOWNERS file is known, opens it at line 1 so the user
   * can verify the file content. Does nothing when no CODEOWNERS file exists.
   */
  private void navigateToMatchedRule() {
    if (project.isDisposed()) return;
    CodeownersOwnerResolution res = resolution;
    CodeownersRule rule = res.rule();
    VirtualFile target;
    int line;
    if (rule != null && rule.sourceFile() != null && rule.sourceFile().isValid()) {
      target = rule.sourceFile();
      line = rule.lineNumber();
    } else {
      target = CodeownersService.getInstance(project).getCodeownersFile();
      if (target == null || !target.isValid()) return;
      line = 0;
    }
    new OpenFileDescriptor(project, target, line, 0).navigate(true);
  }

  private @NotNull String displaySourcePath(@NotNull CodeownersRule rule) {
    VirtualFile vf = rule.sourceFile();
    if (vf == null) return "(unknown)";
    String path = vf.getPath();
    String base = project.getBasePath();
    if (base != null && path.startsWith(base + "/")) {
      return path.substring(base.length() + 1);
    }
    return path;
  }
}
