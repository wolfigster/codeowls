package net.wolfig.codeowls.refactoring;

/**
 * How far a "Refactor Owner" replacement reaches.
 *
 * <p>{@link #SECTION} is only offered when the selected owner actually sits
 * inside a GitLab section — see
 * {@link CodeownersOwnerRefactoring#sectionAt(CharSequence, int)}.
 */
public enum OwnerRefactoringScope {

  /**
   * Every owner occurrence from the enclosing GitLab section header (inclusive,
   * so the header's default owners are covered) up to the next header or EOF.
   */
  SECTION,

  /**
   * Every owner occurrence in the whole CODEOWNERS file.
   */
  FILE,
}
