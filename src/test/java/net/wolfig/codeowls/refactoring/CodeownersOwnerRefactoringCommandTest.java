package net.wolfig.codeowls.refactoring;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import net.wolfig.codeowls.lang.CodeownersFileType;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.OwnerToken;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.Plan;
import net.wolfig.codeowls.refactoring.CodeownersOwnerRefactoring.SectionRange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * End-to-end tests for executing an owner refactoring against a real document:
 * the caret comes from the fixture's {@code <caret>} marker, the plan is built
 * exactly as {@link RefactorCodeownersOwnerAction} builds it, and the edit runs
 * through {@link CodeownersOwnerRefactoringCommand}.
 *
 * <p>Also pins the Undo contract: one refactoring is one command, so a single
 * Undo restores the file no matter how many occurrences were replaced.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersOwnerRefactoringCommandTest {

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

  private Document document() {
    return fixture.getEditor().getDocument();
  }

  /**
   * Replaces the owner under the caret, the way the action does.
   */
  private void refactorAtCaret(String newOwner, OwnerRefactoringScope scope) {
    EdtTestUtil.runInEdtAndWait(() -> {
      Document document = document();
      CharSequence content = document.getImmutableCharSequence();
      OwnerToken owner = CodeownersOwnerRefactoring.ownerTokenAt(
              content, fixture.getEditor().getCaretModel().getOffset());
      assertNotNull("caret is not on an owner", owner);

      SectionRange section = CodeownersOwnerRefactoring.sectionAt(content, owner.startOffset());
      Plan plan = CodeownersOwnerRefactoring.plan(content, owner, section, scope, newOwner);
      CodeownersOwnerRefactoringCommand.execute(fixture.getProject(), fixture.getFile(), document, plan);
    });
  }

  private void undo() {
    EdtTestUtil.runInEdtAndWait(() -> fixture.performEditorAction(IdeActions.ACTION_UNDO));
  }

  @Test
  public void execute_fileScope_replacesEveryOccurrenceInTheDocument() {
    // Arrange
    fixture.configureByText(CodeownersFileType.INSTANCE, """
            /backend/** @alice
            
            [Frontend]
            /frontend/** @al<caret>ice @frontend-team
            
            [Docs]
            /docs/** @alice
            """);

    // Act
    refactorAtCaret("@bob", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("""
            /backend/** @bob
            
            [Frontend]
            /frontend/** @bob @frontend-team
            
            [Docs]
            /docs/** @bob
            """, document().getText());
  }

  @Test
  public void execute_sectionScope_replacesOnlyInsideTheSection() {
    // Arrange
    fixture.configureByText(CodeownersFileType.INSTANCE, """
            /backend/** @alice
            
            [Frontend]
            /frontend/** @al<caret>ice
            /frontend/components/** @alice
            
            [Docs]
            /docs/** @alice
            """);

    // Act
    refactorAtCaret("@bob", OwnerRefactoringScope.SECTION);

    // Assert
    assertEquals("""
            /backend/** @alice
            
            [Frontend]
            /frontend/** @bob
            /frontend/components/** @bob
            
            [Docs]
            /docs/** @alice
            """, document().getText());
  }

  @Test
  public void execute_isUndoneByASingleUndo() {
    // Arrange
    String original = """
            [Backend]
            /api/** @alice
            /db/** @alice
            /jobs/** @alice
            """;
    fixture.configureByText(CodeownersFileType.INSTANCE, """
            [Backend]
            /api/** @al<caret>ice
            /db/** @alice
            /jobs/** @alice
            """);

    // Act — three occurrences replaced, then one Undo.
    refactorAtCaret("@bob", OwnerRefactoringScope.FILE);
    assertNotEquals(original, document().getText());
    undo();

    // Assert
    assertEquals(original, document().getText());
  }

  @Test
  public void execute_replacingWithTheSameOwner_leavesTheDocumentUntouched() {
    // Arrange
    String content = "/api/** @al<caret>ice\n/ui/** @alice\n";
    fixture.configureByText(CodeownersFileType.INSTANCE, content);

    // Act
    refactorAtCaret("@alice", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** @alice\n/ui/** @alice\n", document().getText());
  }

  @Test
  public void execute_duplicateOwner_isCollapsedInTheDocument() {
    // Arrange
    fixture.configureByText(CodeownersFileType.INSTANCE, "/api/** @al<caret>ice @bob\n");

    // Act
    refactorAtCaret("@bob", OwnerRefactoringScope.FILE);

    // Assert
    assertEquals("/api/** @bob\n", document().getText());
  }

  @Test
  public void execute_sectionHeaderDefaultOwner_isReplaced() {
    // Arrange
    fixture.configureByText(CodeownersFileType.INSTANCE, """
            [Backend][2] @alice
            /api/** @al<caret>ice
            /db/**
            
            [Frontend]
            /ui/** @alice
            """);

    // Act
    refactorAtCaret("@bob", OwnerRefactoringScope.SECTION);

    // Assert — the header's default owner is part of the section.
    assertEquals("""
            [Backend][2] @bob
            /api/** @bob
            /db/**
            
            [Frontend]
            /ui/** @alice
            """, document().getText());
  }
}
