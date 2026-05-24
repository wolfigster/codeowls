package net.wolfig.codeowls.navigation;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Project-level resolver of owner → web-page URLs, backed by the repository's
 * Git remote.
 *
 * <p>The hosting platform (GitHub / GitLab / …) and host name are read from
 * {@code .git/config} under the project root and cached, keyed by the config
 * file's last-modified time so a changed remote is picked up automatically.
 * The file is tiny, so it is read on demand; when the project is not a Git
 * repository — or has no remote — {@link #ownerUrl} returns {@code null} and
 * owners are simply not turned into links.
 */
@Service(Service.Level.PROJECT)
public final class CodeownersRemoteService {

  private final Project project;
  private volatile Cache cache;
  /**
   * Non-null only in tests, where it pins the resolver and bypasses disk I/O.
   */
  private volatile RemoteUrlResolver testResolver;

  public CodeownersRemoteService(@NotNull Project project) {
    this.project = project;
  }

  public static @NotNull CodeownersRemoteService getInstance(@NotNull Project project) {
    return project.getService(CodeownersRemoteService.class);
  }

  private static @Nullable Path regularOrNull(@NotNull Path path) {
    return Files.isRegularFile(path) ? path : null;
  }

  static @Nullable Path resolveGitdirFile(@NotNull Path gitFile) {
    try {
      for (String line : Files.readAllLines(gitFile, StandardCharsets.UTF_8)) {
        String t = line.trim();
        if (t.startsWith("gitdir:")) {
          String p = t.substring("gitdir:".length()).trim();
          if (p.isEmpty()) return null;
          Path path = Path.of(p);
          return path.isAbsolute() ? path : gitFile.getParent().resolve(path).normalize();
        }
      }
    } catch (IOException | RuntimeException ignored) {
      // Unreadable / malformed .git pointer — treat as "no remote".
    }
    return null;
  }

  /**
   * Reads the {@code url} of the {@code [remote "origin"]} section, falling
   * back to the first remote URL found if there is no origin.
   */
  static @Nullable String readRemoteUrl(@NotNull Path config) {
    try {
      String section = null;
      String originUrl = null;
      String anyRemoteUrl = null;
      for (String raw : Files.readAllLines(config, StandardCharsets.UTF_8)) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
        if (line.startsWith("[")) {
          int close = line.indexOf(']');
          section = close > 0 ? line.substring(1, close).trim().toLowerCase(Locale.ROOT) : null;
          continue;
        }
        if (section == null || !section.startsWith("remote ")) continue;
        int eq = line.indexOf('=');
        if (eq < 0 || !line.substring(0, eq).trim().equalsIgnoreCase("url")) continue;
        String value = line.substring(eq + 1).trim();
        if (value.isEmpty()) continue;
        if (anyRemoteUrl == null) anyRemoteUrl = value;
        if (section.equals("remote \"origin\"") && originUrl == null) originUrl = value;
      }
      return originUrl != null ? originUrl : anyRemoteUrl;
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
  }

  private static long lastModified(@NotNull Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException ignored) {
      return -1L;
    }
  }

  /**
   * @return the web page for {@code ownerToken} on the project's Git host, or
   * {@code null} if there is no remote or the token has no page (role / e-mail).
   */
  public @Nullable String ownerUrl(@NotNull String ownerToken) {
    RemoteUrlResolver resolver = resolver();
    return resolver == null ? null : resolver.urlForOwner(ownerToken);
  }

  /**
   * Test seam — pin the owner → URL mapping to a fixed remote URL, bypassing
   * the {@code .git/config} read. Mirrors
   * {@link net.wolfig.codeowls.completion.CodeownersGitContributorService#setCachedContributorsForTesting}.
   * Pass {@code null} to clear and fall back to disk resolution.
   */
  void setRemoteUrlForTesting(@Nullable String remoteUrl) {
    this.testResolver = remoteUrl == null ? null : RemoteUrlResolver.fromRemoteUrl(remoteUrl);
  }

  private @Nullable RemoteUrlResolver resolver() {
    RemoteUrlResolver injected = testResolver;
    if (injected != null) return injected;
    if (project.isDisposed()) return null;
    Path config = locateGitConfig();
    if (config == null) return null;
    long mtime = lastModified(config);
    Cache c = cache;
    if (c != null && c.config.equals(config) && c.mtime == mtime) return c.resolver;
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl(readRemoteUrl(config));
    cache = new Cache(config, mtime, resolver);
    return resolver;
  }

  private @Nullable Path locateGitConfig() {
    String basePath = project.getBasePath();
    if (basePath == null) return null;
    Path git = Path.of(basePath, ".git");
    if (Files.isDirectory(git)) {
      return regularOrNull(git.resolve("config"));
    }
    if (Files.isRegularFile(git)) {
      // Worktree / submodule: ".git" is a file pointing at the real git dir.
      Path real = resolveGitdirFile(git);
      return real == null ? null : regularOrNull(real.resolve("config"));
    }
    return null;
  }

  private record Cache(@NotNull Path config, long mtime, @Nullable RemoteUrlResolver resolver) {
  }
}
