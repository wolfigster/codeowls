package net.wolfig.codeowls.explain;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import net.wolfig.codeowls.statusbar.CodeownersOwnerResolution;
import net.wolfig.codeowls.statusbar.CodeownersService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end tests for {@link CodeownersService#explain} and the resulting
 * {@link OwnershipExplanation} — the model behind the "Explain CODEOWNERS
 * Ownership" action.
 *
 * <p>Uses {@link CodeInsightTestFixture} so {@link VirtualFile} and content-root
 * resolution behave like a real IDE, mirroring {@code CodeownersServiceTest}.
 * Every test also asserts that {@code explain} agrees with {@code resolveOwners}
 * so the two evaluation paths cannot drift.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersOwnershipExplanationTest {

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

  private Project project() {
    return fixture.getProject();
  }

  private OwnershipExplanation explain(VirtualFile file) {
    return ReadAction.compute(() -> CodeownersService.getInstance(project()).explain(file));
  }

  private CodeownersOwnerResolution resolve(VirtualFile file) {
    return ReadAction.compute(() -> CodeownersService.getInstance(project()).resolveOwners(file));
  }

  /**
   * Asserts explain's effective owners match resolveOwners for the same file.
   */
  private void assertAgreesWithResolve(VirtualFile file, OwnershipExplanation explanation) {
    assertEquals("explain must agree with resolveOwners",
            resolve(file).owners(), explanation.effectiveOwners());
  }

  // 1. one matching rule -----------------------------------------------------

  @Test
  public void explain_singleMatchingRule_marksItEffective() {
    // Arrange
    fixture.addFileToProject(".github/CODEOWNERS", "*.java @backend\n");
    VirtualFile file = fixture.addFileToProject("src/Foo.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert
    assertTrue(explanation.hasMatch());
    assertEquals(1, explanation.matchedRules().size());
    assertTrue(explanation.matchedRules().getFirst().effective());
    assertEquals(List.of("@backend"), explanation.effectiveOwners());
    assertAgreesWithResolve(file, explanation);
  }

  // 2. multiple matching rules, last wins ------------------------------------

  @Test
  public void explain_multipleMatchingRules_lastWinsAndAllListedInOrder() {
    // Arrange — three rules match; the last one governs.
    fixture.addFileToProject(".github/CODEOWNERS", """
            /src/** @developers
            /src/payment/** @backend
            /src/payment/*.java @payment-team
            """);
    VirtualFile file = fixture.addFileToProject("src/payment/PaymentService.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert — evaluation order preserved, only the last is effective.
    List<MatchedRule> matched = explanation.matchedRules();
    assertEquals(3, matched.size());
    assertEquals("/src/**", matched.get(0).pattern());
    assertEquals("/src/payment/**", matched.get(1).pattern());
    assertEquals("/src/payment/*.java", matched.get(2).pattern());
    assertFalse(matched.get(0).effective());
    assertFalse(matched.get(1).effective());
    assertTrue(matched.get(2).effective());
    assertEquals(List.of("@payment-team"), explanation.effectiveOwners());
    assertSame(matched.get(2), explanation.effectiveRule());
    assertAgreesWithResolve(file, explanation);
  }

  // 3. no matching rule ------------------------------------------------------

  @Test
  public void explain_noMatchingRule_hasCodeownersButNoMatch() {
    // Arrange — a CODEOWNERS file exists but nothing matches the file.
    fixture.addFileToProject(".github/CODEOWNERS", "*.kt @kotlin\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert
    assertTrue(explanation.hasCodeownersFile());
    assertFalse(explanation.hasMatch());
    assertNull(explanation.effectiveRule());
    assertTrue(explanation.effectiveOwners().isEmpty());
    assertAgreesWithResolve(file, explanation);
  }

  @Test
  public void explain_noCodeownersFile_reportsNoCodeownersFile() {
    // Arrange — no CODEOWNERS anywhere.
    VirtualFile file = fixture.addFileToProject("src/Foo.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert
    assertFalse(explanation.hasCodeownersFile());
    assertFalse(explanation.hasMatch());
  }

  // 4. multiple owners -------------------------------------------------------

  @Test
  public void explain_multipleOwners_returnsAllInOrder() {
    // Arrange
    fixture.addFileToProject(".github/CODEOWNERS",
            "*.java @backend-team @john alice@example.com\n");
    VirtualFile file = fixture.addFileToProject("Foo.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert
    assertEquals(List.of("@backend-team", "@john", "alice@example.com"),
            explanation.effectiveOwners());
    assertAgreesWithResolve(file, explanation);
  }

  // 5. GitLab section default owners -----------------------------------------

  @Test
  public void explain_sectionDefaultOwners_inheritanceIsVisible() {
    // Arrange — the rule inherits its owner from the section default.
    fixture.addFileToProject(".gitlab/CODEOWNERS", """
            [Backend] @backend-team
            /src/payment/**
            """);
    VirtualFile file = fixture.addFileToProject("src/payment/PaymentService.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert
    MatchedRule effective = explanation.effectiveRule();
    assertNotNull(effective);
    assertEquals(List.of("@backend-team"), explanation.effectiveOwners());
    assertTrue(effective.inheritedFromSection());
    assertNotNull(effective.section());
    assertEquals("Backend", effective.section().name());
    assertEquals(List.of("@backend-team"), effective.section().defaultOwners());
    assertAgreesWithResolve(file, explanation);
  }

  // 6. GitLab section approval count -----------------------------------------

  @Test
  public void explain_sectionApprovalCount_isReportedAsEffective() {
    // Arrange
    fixture.addFileToProject(".gitlab/CODEOWNERS", """
            [Backend][2] @backend-team
            /src/payment/**
            """);
    VirtualFile file = fixture.addFileToProject("src/payment/PaymentService.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert
    assertEquals(Integer.valueOf(2), explanation.effectiveApprovalCount());
    assertEquals(Integer.valueOf(2), explanation.effectiveRule().section().approvalCount());
    assertAgreesWithResolve(file, explanation);
  }

  // 7. explicit rule owners overriding section owners ------------------------

  @Test
  public void explain_ruleOwnersOverrideSectionDefault_notMarkedInherited() {
    // Arrange — the matching rule declares its own owner.
    fixture.addFileToProject(".gitlab/CODEOWNERS", """
            [Backend][2] @backend-team
            /src/payment/*.java @payment-team
            """);
    VirtualFile file = fixture.addFileToProject("src/payment/PaymentService.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert — own owners win; section still recorded and approval still inherited.
    MatchedRule effective = explanation.effectiveRule();
    assertNotNull(effective);
    assertEquals(List.of("@payment-team"), explanation.effectiveOwners());
    assertFalse(effective.inheritedFromSection());
    assertEquals(Integer.valueOf(2), explanation.effectiveApprovalCount());
    assertAgreesWithResolve(file, explanation);
  }

  // 8. GitLab optional section -----------------------------------------------

  @Test
  public void explain_optionalSection_recordsOptionalFlag() {
    // Arrange
    fixture.addFileToProject(".gitlab/CODEOWNERS", """
            ^[Optional] @maybe-team
            /docs/**
            """);
    VirtualFile file = fixture.addFileToProject("docs/guide.md", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert
    MatchedRule effective = explanation.effectiveRule();
    assertNotNull(effective);
    assertNotNull(effective.section());
    assertTrue(effective.section().optional());
    assertEquals(List.of("@maybe-team"), explanation.effectiveOwners());
    assertAgreesWithResolve(file, explanation);
  }

  // 9. GitLab negation pattern -----------------------------------------------

  @Test
  public void explain_negationPattern_matchesConsistentlyWithResolver() {
    // Arrange — a GitLab negated pattern. Whatever the shared matcher decides,
    // explain must agree with resolveOwners (the matcher strips the leading !).
    fixture.addFileToProject(".gitlab/CODEOWNERS", """
            /src/** @backend
            !/src/generated/** @backend
            """);
    VirtualFile file = fixture.addFileToProject("src/generated/Api.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert — the explanation lists whatever the shared matcher matches, and
    // its effective owner equals the resolver's. No independent glob logic.
    assertAgreesWithResolve(file, explanation);
    if (explanation.hasMatch()) {
      assertTrue(explanation.matchedRules().stream()
              .anyMatch(r -> r.pattern().contains("generated")));
    }
  }

  // 10. navigation / source information ---------------------------------------

  @Test
  public void explain_matchedRule_carriesCorrectSourceFileAndLine() {
    // Arrange — the matching rule is on line 3 (0-based: 2).
    VirtualFile codeowners = fixture.addFileToProject(".github/CODEOWNERS", """
            # owners
            *.kt @kotlin
            *.java @backend
            """).getVirtualFile();
    VirtualFile file = fixture.addFileToProject("src/Foo.java", "").getVirtualFile();

    // Act
    OwnershipExplanation explanation = explain(file);

    // Assert — source points at the CODEOWNERS file, at the winning rule's line.
    MatchedRule effective = explanation.effectiveRule();
    assertNotNull(effective);
    assertEquals(codeowners, effective.sourceFile());
    assertEquals(2, effective.line());
    assertEquals("*.java", effective.pattern());
  }
}
