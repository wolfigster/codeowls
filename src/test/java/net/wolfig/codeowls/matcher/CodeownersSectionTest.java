package net.wolfig.codeowls.matcher;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CodeownersSection} — a pure data record with a single piece
 * of behavior, {@link CodeownersSection#displayHeader()}, which reconstructs
 * the GitLab section header roughly as it appears in the file.
 *
 * <p>Tests follow the Arrange / Act / Assert pattern.
 */
public class CodeownersSectionTest {

  @Test
  public void displayHeader_optionalWithApprovalAndOwners_rendersAllParts() {
    // Arrange
    CodeownersSection section = new CodeownersSection(
            "Backend", true, List.of("@org/backend", "@alice"), 2);

    // Act
    String header = section.displayHeader();

    // Assert
    assertEquals("^[Backend][2] @org/backend @alice", header);
  }

  @Test
  public void displayHeader_mandatorySectionNoApprovalSingleOwner_rendersNameAndOwner() {
    // Arrange
    CodeownersSection section = new CodeownersSection(
            "Documentation", false, List.of("@docs-team"), null);

    // Act
    String header = section.displayHeader();

    // Assert
    assertEquals("[Documentation] @docs-team", header);
  }

  @Test
  public void displayHeader_noOwnersNoApproval_rendersJustTheName() {
    // Arrange
    CodeownersSection section = new CodeownersSection("Misc", false, List.of(), null);

    // Act
    String header = section.displayHeader();

    // Assert
    assertEquals("[Misc]", header);
  }

  @Test
  public void displayHeader_approvalCountButNoOwners_rendersNameAndCount() {
    // Arrange
    CodeownersSection section = new CodeownersSection("Backend", false, List.of(), 3);

    // Act
    String header = section.displayHeader();

    // Assert
    assertEquals("[Backend][3]", header);
  }

  @Test
  public void accessors_returnConstructorValues() {
    // Arrange
    CodeownersSection section = new CodeownersSection(
            "Backend", true, List.of("@a", "@b"), 1);

    // Assert
    assertEquals("Backend", section.name());
    assertTrue(section.optional());
    assertEquals(List.of("@a", "@b"), section.defaultOwners());
    assertEquals(Integer.valueOf(1), section.approvalCount());
  }
}
