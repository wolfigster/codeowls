package net.wolfig.codeowls.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import net.wolfig.codeowls.search.OwnershipSearchService.OwnedFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Effective-ownership search tests. The service is exercised against a real
 * project file index and the shared {@code CodeownersService} resolver.
 */
public class OwnershipSearchServiceTest {

  private CodeInsightTestFixture fixture;

  @Before
  public void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder =
            factory.createLightFixtureBuilder(getClass().getName());
    fixture = factory.createCodeInsightFixture(builder.getFixture());
    fixture.setUp();
  }

  @After
  public void tearDown() throws Exception {
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  private PsiFile codeowners(String content) {
    return fixture.addFileToProject(".github/CODEOWNERS", content);
  }

  private void file(String path) {
    fixture.addFileToProject(path, "");
  }

  private List<String> find(String owner, PsiFile codeowners) {
    List<OwnedFile> matches = ReadAction.compute(() ->
            OwnershipSearchService.getInstance(fixture.getProject()).findFilesOwnedBy(
                    owner,
                    codeowners.getVirtualFile(),
                    new EmptyProgressIndicator()));
    return matches.stream().map(OwnedFile::relativePath).toList();
  }

  @Test
  public void findFilesOwnedBy_oneEffectiveFile_returnsIt() {
    PsiFile codeowners = codeowners("/src/Foo.java @backend\n");
    file("src/Foo.java");
    file("src/Bar.java");

    assertEquals(List.of("src/Foo.java"), find("@backend", codeowners));
  }

  @Test
  public void findFilesOwnedBy_multipleEffectiveFiles_returnsAllSorted() {
    PsiFile codeowners = codeowners("/src/** @backend\n");
    file("src/Z.java");
    file("src/A.java");
    file("docs/README.md");

    assertEquals(List.of("src/A.java", "src/Z.java"), find("@backend", codeowners));
  }

  @Test
  public void findFilesOwnedBy_ownerWithNoFiles_returnsEmpty() {
    PsiFile codeowners = codeowners("*.kt @kotlin\n");
    file("src/Foo.java");

    assertEquals(List.of(), find("@kotlin", codeowners));
  }

  @Test
  public void findFilesOwnedBy_laterSpecificRule_excludesOverriddenFile() {
    PsiFile codeowners = codeowners("""
            /src/** @developers
            /src/backend/** @backend-team
            /src/backend/payment/** @payment-team
            """);
    file("src/backend/UserService.java");
    file("src/backend/order/OrderService.java");
    file("src/backend/payment/PaymentService.java");

    assertEquals(
            List.of("src/backend/UserService.java", "src/backend/order/OrderService.java"),
            find("@backend-team", codeowners));
  }

  @Test
  public void findFilesOwnedBy_completelyShadowedOwner_returnsEmpty() {
    PsiFile codeowners = codeowners("""
            /src/** @backend-team
            /src/** @platform-team
            """);
    file("src/Foo.java");

    assertEquals(List.of(), find("@backend-team", codeowners));
    assertEquals(List.of("src/Foo.java"), find("@platform-team", codeowners));
  }

  @Test
  public void findFilesOwnedBy_multipleOwners_eachOwnsTheFile() {
    PsiFile codeowners = codeowners(
            "/src/** @backend-team @alice backend@example.com\n");
    file("src/Foo.java");

    assertEquals(List.of("src/Foo.java"), find("@backend-team", codeowners));
    assertEquals(List.of("src/Foo.java"), find("@alice", codeowners));
    assertEquals(List.of("src/Foo.java"), find("backend@example.com", codeowners));
  }

  @Test
  public void findFilesOwnedBy_similarOwnerNames_comparesWholeTokens() {
    PsiFile codeowners = codeowners("""
            /alice/** @alice
            /team/** @alice-team
            """);
    file("alice/One.java");
    file("team/Two.java");

    assertEquals(List.of("alice/One.java"), find("@alice", codeowners));
    assertEquals(List.of("team/Two.java"), find("@alice-team", codeowners));
  }

  @Test
  public void findFilesOwnedBy_emailAndGitLabRole_supportsBothTokenTypes() {
    PsiFile codeowners = codeowners("""
            /mail/** alice@example.com
            /ops/** @@maintainer
            """);
    file("mail/Notice.txt");
    file("ops/deploy.yml");

    assertEquals(List.of("mail/Notice.txt"), find("alice@example.com", codeowners));
    assertEquals(List.of("ops/deploy.yml"), find("@@maintainer", codeowners));
  }

  @Test
  public void findFilesOwnedBy_sectionDefaultAndInheritedRules_returnsBothFiles() {
    PsiFile codeowners = codeowners("""
            [Backend] @backend-team
            /src/backend/**
            /src/api/**
            """);
    file("src/backend/Foo.java");
    file("src/api/Api.java");

    assertEquals(
            List.of("src/api/Api.java", "src/backend/Foo.java"),
            find("@backend-team", codeowners));
  }

  @Test
  public void findFilesOwnedBy_explicitRuleOwner_overridesSectionDefault() {
    PsiFile codeowners = codeowners("""
            [Backend] @backend-team
            /src/backend/**
            /src/backend/payment/** @payment-team
            """);
    file("src/backend/Foo.java");
    file("src/backend/payment/Payment.java");

    assertEquals(List.of("src/backend/Foo.java"), find("@backend-team", codeowners));
    assertEquals(List.of("src/backend/payment/Payment.java"), find("@payment-team", codeowners));
  }

  @Test
  public void findFilesOwnedBy_multipleSections_keepsDefaultsSeparate() {
    PsiFile codeowners = codeowners("""
            [Backend] @backend-team
            /src/backend/**
            [Frontend] @frontend-team
            /src/frontend/**
            """);
    file("src/backend/Foo.java");
    file("src/frontend/App.ts");

    assertEquals(List.of("src/backend/Foo.java"), find("@backend-team", codeowners));
    assertEquals(List.of("src/frontend/App.ts"), find("@frontend-team", codeowners));
  }

  @Test
  public void findFilesOwnedBy_ownerInSeveralRules_combinesEffectiveFiles() {
    PsiFile codeowners = codeowners("""
            /src/api/** @platform
            /src/jobs/** @platform
            /src/jobs/private/** @private
            """);
    file("src/api/Api.java");
    file("src/jobs/Job.java");
    file("src/jobs/private/SecretJob.java");

    assertEquals(
            List.of("src/api/Api.java", "src/jobs/Job.java"),
            find("@platform", codeowners));
  }

  @Test
  public void findFilesOwnedBy_exactWildcardAndDirectoryRules_allResolveNormally() {
    PsiFile codeowners = codeowners("""
            /exact.txt @exact
            *.java @java
            /docs/ @docs
            """);
    file("exact.txt");
    file("src/Foo.java");
    file("docs/README.md");
    file("docs/guide/intro.md");

    assertEquals(List.of("exact.txt"), find("@exact", codeowners));
    assertEquals(List.of("src/Foo.java"), find("@java", codeowners));
    assertEquals(
            List.of("docs/README.md", "docs/guide/intro.md"),
            find("@docs", codeowners));
  }

  @Test
  public void findFilesOwnedBy_gitLabNegation_usesSharedResolverSemantics() {
    PsiFile codeowners = codeowners("!/generated/** @generated-owner\n");
    file("generated/Client.java");
    file("src/App.java");

    assertEquals(List.of("generated/Client.java"), find("@generated-owner", codeowners));
  }

  @Test
  public void findFilesOwnedBy_scansOnlySelectedCodeownersRepositoryRoot() {
    PsiFile codeowners = fixture.addFileToProject(
            "repo/.github/CODEOWNERS", "*.java @repo-owner\n");
    file("repo/src/InRepo.java");
    file("outside/Outside.java");

    assertEquals(List.of("src/InRepo.java"), find("@repo-owner", codeowners));
  }

  @Test
  public void findFilesOwnedBy_documentModification_isReflectedImmediately() {
    PsiFile codeowners = codeowners("/src/Foo.java @first\n");
    file("src/Foo.java");
    assertEquals(List.of("src/Foo.java"), find("@first", codeowners));

    VirtualFile codeownersFile = codeowners.getVirtualFile();
    Document document = ReadAction.compute(() ->
            FileDocumentManager.getInstance().getDocument(codeownersFile));
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(
            fixture.getProject(),
            () -> document.setText("/src/Foo.java @updated\n"));

    assertEquals(List.of(), find("@first", codeowners));
    assertEquals(List.of("src/Foo.java"), find("@updated", codeowners));
  }
}
