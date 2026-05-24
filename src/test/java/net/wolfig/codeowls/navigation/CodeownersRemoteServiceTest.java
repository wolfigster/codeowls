package net.wolfig.codeowls.navigation;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for the file-reading internals of {@link CodeownersRemoteService} —
 * the {@code .git/config} remote-URL parser and the worktree {@code gitdir:}
 * pointer resolution. These are plain file-system operations, so they run
 * without the IntelliJ platform fixture against files in a temporary directory.
 *
 * <p>The owner → URL mapping itself lives in {@link RemoteUrlResolver} and is
 * covered by {@link RemoteUrlResolverTest}; here we only verify which remote
 * URL the service feeds into it.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersRemoteServiceTest {

  private Path tempDir;

  @After
  public void tearDown() throws IOException {
    if (tempDir != null && Files.exists(tempDir)) {
      try (var paths = Files.walk(tempDir)) {
        paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> p.toFile().delete());
      }
      tempDir = null;
    }
  }

  private Path writeConfig(String content) throws IOException {
    tempDir = Files.createTempDirectory("codeowls-git");
    Path config = tempDir.resolve("config");
    Files.writeString(config, content, StandardCharsets.UTF_8);
    return config;
  }

  // -- readRemoteUrl -------------------------------------------------------

  @Test
  public void readRemoteUrl_originSshRemote_returnsItsUrl() throws IOException {
    // Arrange — a typical config written by `git clone` over SSH.
    Path config = writeConfig("""
            [core]
            \trepositoryformatversion = 0
            [remote "origin"]
            \turl = git@github.com:org/repo.git
            \tfetch = +refs/heads/*:refs/remotes/origin/*
            [branch "main"]
            \tremote = origin
            """);

    // Act
    String url = CodeownersRemoteService.readRemoteUrl(config);

    // Assert
    assertEquals("git@github.com:org/repo.git", url);
  }

  @Test
  public void readRemoteUrl_originHttpsRemote_returnsItsUrl() throws IOException {
    // Arrange
    Path config = writeConfig("""
            [remote "origin"]
            \turl = https://gitlab.com/group/repo.git
            """);

    // Act
    String url = CodeownersRemoteService.readRemoteUrl(config);

    // Assert
    assertEquals("https://gitlab.com/group/repo.git", url);
  }

  @Test
  public void readRemoteUrl_originPreferredOverOtherRemotes_regardlessOfOrder() throws IOException {
    // Arrange — an "upstream" remote precedes "origin"; origin must still win.
    Path config = writeConfig("""
            [remote "upstream"]
            \turl = git@github.com:upstream/repo.git
            [remote "origin"]
            \turl = git@github.com:fork/repo.git
            """);

    // Act
    String url = CodeownersRemoteService.readRemoteUrl(config);

    // Assert
    assertEquals("git@github.com:fork/repo.git", url);
  }

  @Test
  public void readRemoteUrl_noOriginButAnotherRemote_fallsBackToThatRemote() throws IOException {
    // Arrange — no origin defined.
    Path config = writeConfig("""
            [remote "upstream"]
            \turl = git@github.com:upstream/repo.git
            """);

    // Act
    String url = CodeownersRemoteService.readRemoteUrl(config);

    // Assert
    assertEquals("git@github.com:upstream/repo.git", url);
  }

  @Test
  public void readRemoteUrl_commentsAndBlankLines_areIgnored() throws IOException {
    // Arrange
    Path config = writeConfig("""
            # a comment
            ; another comment
            
            [remote "origin"]
            \turl = git@github.com:org/repo.git
            """);

    // Act
    String url = CodeownersRemoteService.readRemoteUrl(config);

    // Assert
    assertEquals("git@github.com:org/repo.git", url);
  }

  @Test
  public void readRemoteUrl_noRemoteSection_returnsNull() throws IOException {
    // Arrange — a repo with no remotes configured.
    Path config = writeConfig("""
            [core]
            \tbare = false
            [branch "main"]
            \tremote = origin
            """);

    // Act / Assert
    assertNull(CodeownersRemoteService.readRemoteUrl(config));
  }

  @Test
  public void readRemoteUrl_remoteWithoutUrlKey_returnsNull() throws IOException {
    // Arrange — a remote section that only has a fetch refspec.
    Path config = writeConfig("""
            [remote "origin"]
            \tfetch = +refs/heads/*:refs/remotes/origin/*
            """);

    // Act / Assert
    assertNull(CodeownersRemoteService.readRemoteUrl(config));
  }

  @Test
  public void readRemoteUrl_missingFile_returnsNull() {
    // Arrange — a path that does not exist.
    Path missing = Path.of(System.getProperty("java.io.tmpdir"), "codeowls-no-such-config-" + System.nanoTime());

    // Act / Assert — the read fails and is swallowed as "no remote".
    assertNull(CodeownersRemoteService.readRemoteUrl(missing));
  }

  // -- resolveGitdirFile (worktree / submodule ".git" pointer) -------------

  @Test
  public void resolveGitdirFile_relativeGitdir_resolvedAgainstParent() throws IOException {
    // Arrange — a worktree's ".git" file points at the real git directory.
    tempDir = Files.createTempDirectory("codeowls-worktree");
    Path gitFile = tempDir.resolve(".git");
    Files.writeString(gitFile, "gitdir: ../.git/worktrees/wt\n", StandardCharsets.UTF_8);

    // Act
    Path resolved = CodeownersRemoteService.resolveGitdirFile(gitFile);

    // Assert
    assertEquals(tempDir.getParent().resolve(".git/worktrees/wt").normalize(), resolved);
  }

  @Test
  public void resolveGitdirFile_absoluteGitdir_returnedAsIs() throws IOException {
    // Arrange
    tempDir = Files.createTempDirectory("codeowls-worktree");
    Path gitFile = tempDir.resolve(".git");
    Path absolute = tempDir.resolve("real-git-dir");
    Files.writeString(gitFile, "gitdir: " + absolute + "\n", StandardCharsets.UTF_8);

    // Act
    Path resolved = CodeownersRemoteService.resolveGitdirFile(gitFile);

    // Assert
    assertEquals(absolute, resolved);
  }

  @Test
  public void resolveGitdirFile_noGitdirLine_returnsNull() throws IOException {
    // Arrange — a ".git" file without the expected pointer.
    tempDir = Files.createTempDirectory("codeowls-worktree");
    Path gitFile = tempDir.resolve(".git");
    Files.writeString(gitFile, "not a gitdir pointer\n", StandardCharsets.UTF_8);

    // Act / Assert
    assertNull(CodeownersRemoteService.resolveGitdirFile(gitFile));
  }
}
