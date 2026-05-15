package net.wolfig.codeowls.completion;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Project-level cache of unique Git author emails, used by
 * {@link CodeownersGitOwnerSource} to seed completion with people who have
 * actually touched the codebase.
 *
 * <p>The service never blocks the caller: {@link #getCachedContributors()}
 * returns whatever is currently cached and asynchronously triggers a refresh
 * if the TTL has elapsed. The refresh uses {@code git log --format=%ae} via
 * {@link GeneralCommandLine}, bounded to {@value #MAX_COMMITS} commits and
 * {@value #TIMEOUT_MS} ms so it stays cheap even on large repositories. If
 * the {@code git} binary is missing, the working directory isn't a repo, or
 * the command times out, the result is an empty list — the rest of the
 * completion experience still works.
 *
 * <p>The {@code git log} call deliberately runs on a pooled background
 * thread; the service avoids ever reaching VFS or PSI to keep refresh
 * thread-safety simple.
 */
@Service(Service.Level.PROJECT)
public final class CodeownersGitContributorService {

  /**
   * How long a cached result stays fresh before another refresh is scheduled.
   */
  static final long TTL_MS = 5L * 60 * 1000;
  /**
   * Hard cap on commits walked by {@code git log}, to bound runtime on big repos.
   */
  static final int MAX_COMMITS = 5000;
  /**
   * Hard cap on the time spent waiting for {@code git} to respond.
   */
  static final int TIMEOUT_MS = 5000;

  private final Project project;
  private final Object refreshLock = new Object();
  private volatile List<String> cached = List.of();
  private volatile long lastRefreshMs = 0L;
  private boolean refreshing = false;

  public CodeownersGitContributorService(@NotNull Project project) {
    this.project = project;
  }

  public static @NotNull CodeownersGitContributorService getInstance(@NotNull Project project) {
    return project.getService(CodeownersGitContributorService.class);
  }

  private static @Nullable File toLocalDir(@Nullable VirtualFile vf) {
    if (vf == null || !vf.isValid() || !vf.isInLocalFileSystem()) return null;
    File f = new File(vf.getPath());
    return f.isDirectory() ? f : null;
  }

  /**
   * Drives {@code git log -<n> --format=%ae --no-merges}. Package-private so
   * tests can exercise it directly against a temporary git repo if they want;
   * the rest of the service treats failures uniformly as "no contributors".
   */
  static @NotNull List<String> queryGitAuthors(@NotNull File workDir) {
    GeneralCommandLine cmd = new GeneralCommandLine(
            "git", "log", "-" + MAX_COMMITS, "--format=%ae", "--no-merges");
    cmd.setWorkDirectory(workDir);
    cmd.setCharset(java.nio.charset.StandardCharsets.UTF_8);
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(cmd, TIMEOUT_MS);
      if (output.getExitCode() != 0 || output.isTimeout()) return List.of();
      Set<String> seen = new LinkedHashSet<>();
      for (String line : output.getStdoutLines()) {
        String trimmed = line.trim();
        // Filter to plausibly-email-shaped values to avoid surfacing the
        // empty placeholders Git emits for malformed commits.
        if (trimmed.isEmpty() || trimmed.indexOf('@') <= 0) continue;
        seen.add(trimmed);
      }
      return seen.stream().sorted().toList();
    } catch (ExecutionException ignored) {
      return List.of();
    }
  }

  /**
   * @return the most recently cached list of contributor emails. Triggers a
   * background refresh as a side effect if the TTL has elapsed; never blocks.
   */
  public @NotNull List<String> getCachedContributors() {
    long now = System.currentTimeMillis();
    if (now - lastRefreshMs > TTL_MS) {
      scheduleRefresh();
    }
    return cached;
  }

  private void scheduleRefresh() {
    synchronized (refreshLock) {
      if (refreshing) return;
      refreshing = true;
    }
    ApplicationManager.getApplication().executeOnPooledThread(this::refreshNow);
  }

  private void refreshNow() {
    try {
      if (project.isDisposed()) return;
      File workDir = resolveWorkDir();
      cached = workDir == null ? List.of() : queryGitAuthors(workDir);
      lastRefreshMs = System.currentTimeMillis();
    } finally {
      synchronized (refreshLock) {
        refreshing = false;
      }
    }
  }

  private @Nullable File resolveWorkDir() {
    if (project.isDisposed()) return null;
    VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
    for (VirtualFile root : roots) {
      File f = toLocalDir(root);
      if (f != null) return f;
    }
    String basePath = project.getBasePath();
    if (basePath == null) return null;
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
    File f = toLocalDir(baseDir);
    return f != null ? f : new File(basePath);
  }

  /**
   * Test seam — replace the cached list deterministically without invoking
   * {@code git}. Marks the cache as just-refreshed so the next read won't
   * schedule a background refresh.
   */
  public void setCachedContributorsForTesting(@NotNull List<String> contributors) {
    this.cached = List.copyOf(contributors);
    this.lastRefreshMs = System.currentTimeMillis();
  }
}
