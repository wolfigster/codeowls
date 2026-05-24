package net.wolfig.codeowls.navigation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Maps a CODEOWNERS owner token (e.g. {@code @alice}, {@code @org/team}) to the
 * URL of that user or group on the project's Git hosting platform.
 *
 * <p>The platform and host are derived once from the repository's remote URL
 * (see {@link CodeownersRemoteService}); this class is the pure mapping from an
 * owner string to a web address and carries no IntelliJ or filesystem
 * dependencies, so it can be unit-tested in isolation.
 *
 * <p>Only individual users and teams/groups map to a page. Role owners
 * ({@code @@maintainer}) and e-mail owners have no profile URL and yield
 * {@code null}.
 */
final class RemoteUrlResolver {

  enum Platform {GITHUB, GITLAB, BITBUCKET, GENERIC}

  private final String host;
  private final Platform platform;

  private RemoteUrlResolver(@NotNull String host, @NotNull Platform platform) {
    this.host = host;
    this.platform = platform;
  }

  /**
   * Builds a resolver from a Git remote URL (SSH scp-style or URL form), or
   * {@code null} if no host can be extracted.
   */
  static @Nullable RemoteUrlResolver fromRemoteUrl(@Nullable String remoteUrl) {
    String host = extractHost(remoteUrl);
    if (host == null) return null;
    return new RemoteUrlResolver(host, platformOf(host));
  }

  /**
   * @return the web page for {@code ownerToken} on this host, or {@code null}
   * when the token is not a user or team (roles, e-mails, blanks).
   */
  @Nullable String urlForOwner(@Nullable String ownerToken) {
    if (ownerToken == null) return null;
    String name = ownerToken.startsWith("@") ? ownerToken.substring(1) : ownerToken;
    // "@@role" leaves a leading '@' after stripping one; e-mail owners contain '@'.
    if (name.isEmpty() || name.indexOf('@') >= 0 || name.startsWith("/")) return null;

    if (platform == Platform.GITHUB && name.indexOf('/') >= 0) {
      // GitHub teams live under the org, not the namespace path.
      String org = name.substring(0, name.indexOf('/'));
      String team = name.substring(name.lastIndexOf('/') + 1);
      if (org.isEmpty() || team.isEmpty()) return null;
      return "https://" + host + "/orgs/" + org + "/teams/" + team;
    }
    // Users on every platform, and GitLab/Bitbucket groups, share the namespace path.
    return "https://" + host + "/" + name;
  }

  private static @NotNull Platform platformOf(@NotNull String host) {
    String h = host.toLowerCase(Locale.ROOT);
    if (h.contains("github")) return Platform.GITHUB;
    if (h.contains("gitlab")) return Platform.GITLAB;
    if (h.contains("bitbucket")) return Platform.BITBUCKET;
    return Platform.GENERIC;
  }

  /**
   * Extracts the host from a Git remote URL, handling both the {@code ssh://}
   * / {@code https://} URL form and the {@code git@host:path} scp-like form,
   * dropping any user-info and port. Package-private for unit testing.
   */
  static @Nullable String extractHost(@Nullable String rawUrl) {
    if (rawUrl == null) return null;
    String url = rawUrl.trim();
    if (url.isEmpty()) return null;

    int scheme = url.indexOf("://");
    if (scheme >= 0) {
      String rest = url.substring(scheme + 3);
      int slash = rest.indexOf('/');
      int at = rest.indexOf('@');
      if (at >= 0 && (slash < 0 || at < slash)) rest = rest.substring(at + 1);
      int end = rest.length();
      for (int i = 0; i < rest.length(); i++) {
        char c = rest.charAt(i);
        if (c == '/' || c == ':') {
          end = i;
          break;
        }
      }
      String host = rest.substring(0, end);
      return host.isEmpty() ? null : host;
    }

    // scp-like syntax: [user@]host:path
    int colon = url.indexOf(':');
    if (colon > 0) {
      int at = url.indexOf('@');
      int start = (at >= 0 && at < colon) ? at + 1 : 0;
      String host = url.substring(start, colon);
      return host.isEmpty() ? null : host;
    }
    return null;
  }
}
