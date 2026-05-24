package net.wolfig.codeowls.navigation;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link RemoteUrlResolver} — the pure owner-token → web-URL mapping.
 * Covers host extraction from the SSH scp-style and URL forms and the
 * platform-specific URL shapes (GitHub team pages live under the org; GitLab and
 * generic hosts use the namespace path), plus the non-linkable owner kinds.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class RemoteUrlResolverTest {

  // -- extractHost ---------------------------------------------------------

  @Test
  public void extractHost_sshScpForm_returnsHost() {
    // Act / Assert
    assertEquals("github.com", RemoteUrlResolver.extractHost("git@github.com:org/repo.git"));
  }

  @Test
  public void extractHost_httpsForm_returnsHost() {
    // Act / Assert
    assertEquals("gitlab.com", RemoteUrlResolver.extractHost("https://gitlab.com/group/repo.git"));
  }

  @Test
  public void extractHost_sshUrlWithUserAndPort_returnsHostOnly() {
    // Act / Assert — user-info and port are dropped.
    assertEquals("gitlab.example.com",
            RemoteUrlResolver.extractHost("ssh://git@gitlab.example.com:22/group/repo.git"));
  }

  @Test
  public void extractHost_httpsWithEmbeddedCredentials_dropsUserInfo() {
    // Act / Assert
    assertEquals("github.com",
            RemoteUrlResolver.extractHost("https://user:token@github.com/org/repo.git"));
  }

  @Test
  public void extractHost_blankOrNull_returnsNull() {
    // Act / Assert
    assertNull(RemoteUrlResolver.extractHost(null));
    assertNull(RemoteUrlResolver.extractHost("   "));
  }

  // -- GitHub --------------------------------------------------------------

  @Test
  public void urlForOwner_githubUser_pointsToProfile() {
    // Arrange
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("git@github.com:org/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertEquals("https://github.com/alice", resolver.urlForOwner("@alice"));
  }

  @Test
  public void urlForOwner_githubTeam_pointsToOrgTeamsPage() {
    // Arrange
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("https://github.com/acme/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertEquals("https://github.com/orgs/acme/teams/backend", resolver.urlForOwner("@acme/backend"));
  }

  // -- GitLab / self-hosted ------------------------------------------------

  @Test
  public void urlForOwner_gitlabUser_pointsToProfile() {
    // Arrange
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("https://gitlab.com/group/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertEquals("https://gitlab.com/bob", resolver.urlForOwner("@bob"));
  }

  @Test
  public void urlForOwner_gitlabGroupPath_keptAsNamespacePath() {
    // Arrange — GitLab groups/subgroups are addressed by their namespace path.
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("git@gitlab.com:group/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertEquals("https://gitlab.com/group/subgroup", resolver.urlForOwner("@group/subgroup"));
  }

  @Test
  public void urlForOwner_selfHostedGitlab_usesRemoteHost() {
    // Arrange
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("git@gitlab.example.com:group/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertEquals("https://gitlab.example.com/bob", resolver.urlForOwner("@bob"));
  }

  @Test
  public void fromRemoteUrl_unknownHost_buildsGenericUserPath() {
    // Arrange — a host matching no known platform falls back to the namespace path.
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("git@git.acme.internal:team/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertEquals("https://git.acme.internal/alice", resolver.urlForOwner("@alice"));
  }

  // -- non-linkable owners -------------------------------------------------

  @Test
  public void urlForOwner_roleOwner_returnsNull() {
    // Arrange — GitLab roles (@@maintainer) are not pages.
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("git@github.com:org/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertNull(resolver.urlForOwner("@@maintainer"));
  }

  @Test
  public void urlForOwner_emailOwner_returnsNull() {
    // Arrange — e-mail owners have no profile page.
    RemoteUrlResolver resolver = RemoteUrlResolver.fromRemoteUrl("git@github.com:org/repo.git");

    // Act / Assert
    assertNotNull(resolver);
    assertNull(resolver.urlForOwner("alice@example.com"));
  }
}
