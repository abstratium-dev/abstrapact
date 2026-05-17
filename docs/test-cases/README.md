# Test Cases

This directory contains structured test cases for the Abstraccount application, written in Gherkin-style format following industry standards (BDD - Behavior-Driven Development).

## Structure

Each test case is stored in a separate file with a three-digit numbering scheme:

- `001-create-journal-with-accounts.md` - First test case
- `002-next-test-case.md` - Second test case
- etc.

## File Naming Convention

- **Format:** `NNN-descriptive-name.md`
- **NNN:** Three-digit number (001, 002, 003, etc.)
- **descriptive-name:** Kebab-case description of the test case
- **Extension:** `.md` (Markdown)

## Test Case Format

Each test case follows this structure:

### Header Section
- **Feature:** The feature being tested
- **Priority:** High/Medium/Low
- **Status:** Draft/Review/Approved/Implemented
- **Author:** Who created the test case
- **Date:** Creation date

### Main Sections
1. **Preconditions** - References [PRECONDITIONS.md](./PRECONDITIONS.md) and any specific preconditions
2. **Test Objective** - What the test aims to verify
3. **Test Data** - Specific data used in the test
4. **Test Steps** - Gherkin-formatted scenarios
5. **Expected Results** - What should happen
6. **Acceptance Criteria** - Checklist of requirements
7. **Notes** - Additional information
8. **Related Test Cases** - Links to related tests
9. **Tags** - Searchable tags

## Gherkin Syntax

Test steps use Gherkin syntax with the following keywords:

- **Feature:** High-level description of a software feature
- **Background:** Steps that run before each scenario
- **Scenario:** Concrete example of business rule
- **Given:** Preconditions and initial state
- **When:** Actions taken by the user
- **And:** Additional conditions or actions
- **Then:** Expected outcomes
- **But:** Negative assertions

### Example

```gherkin
Feature: User Authentication

  Background:
    Given the application is running
    And the user is on the login page

  Scenario: Successful login with valid credentials
    When the user enters "user@example.com" as the email
    And the user enters "password123" as the password
    And the user clicks "Sign In"
    Then the user should be redirected to the dashboard
    And a welcome message should be displayed
```

## General Preconditions

All test cases share common preconditions defined in [PRECONDITIONS.md](./PRECONDITIONS.md):

- User is authenticated and signed in
- Application is running and accessible
- Database is in a clean, known state
- Browser environment is properly configured

Individual test cases may specify additional preconditions or override these defaults.

## Usage

1. **For Manual Testing:**
   - Follow the test steps in sequence
   - Verify expected results at each step
   - Check off acceptance criteria as they are met

2. **For Automated Testing:**
   - Use these test cases as specifications for automated tests
   - Implement test automation using frameworks like Playwright, Cucumber, etc.
   - Map Gherkin steps to test automation code

3. **For Test Generation:**
   - These test cases can be used as input for AI-assisted test generation
   - The structured format allows for automated test code generation
   - Modify test cases as needed before generating automated tests

## Test Case Lifecycle

1. **Draft** - Initial creation, not yet reviewed
2. **Review** - Under review by team members
3. **Approved** - Reviewed and approved for implementation
4. **Implemented** - Automated tests have been created
5. **Deprecated** - No longer applicable (with reason noted)

## Contributing

When adding new test cases:

1. Use the next available three-digit number
2. Follow the established format and structure
3. Include all required sections
4. Reference [PRECONDITIONS.md](./PRECONDITIONS.md) appropriately
5. Add relevant tags for searchability
6. Link to related test cases
7. Update this README if adding new conventions

## Test Coverage

Test cases should cover:

- **Happy paths** - Normal, expected user flows
- **Edge cases** - Boundary conditions and unusual inputs
- **Error handling** - Invalid inputs and error states
- **Integration** - Interactions between components
- **End-to-end** - Complete user workflows

## References

- [Gherkin Reference](https://cucumber.io/docs/gherkin/reference/)
- [Behavior-Driven Development (BDD)](https://cucumber.io/docs/bdd/)
- [PRECONDITIONS.md](./PRECONDITIONS.md) - General test preconditions
- [../DEVELOPMENT_AND_TESTING.md](../DEVELOPMENT_AND_TESTING.md) - Testing environment setup

## Current Test Cases

| Number | Name | Feature | Priority | Status |
|--------|------|---------|----------|--------|
| 001 | Create Journal with Accounts | Journal and Account Management | High | Draft |

---

**Note:** This directory contains test specifications, not automated test code. Automated tests are located in `/e2e-tests/` and `/src/test/`.
