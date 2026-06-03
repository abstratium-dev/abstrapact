---
trigger: glob
globs: src/test/resources/features/*.feature
---

User stories and their acceptance criteria are written as part of `feature` files, since we use gherkin in this project. If you need a reference, see here: https://cucumber.io/docs/gherkin/reference .

Use the following template, and store the gherkin files in the folder src/test/resources/features in files named `<simple-description>.feature`, where `<simple-description>` is a short 2-4 word description of the feature, delimited with hyphens because they are file names.

## User Story Template

```
Feature: [Feature Name]
  
  User Story:
  As a [type of user]
  I want [goal/desire]
  So that [benefit/reason]
  
  Acceptance Criteria:
  - [Criterion 1]
  - [Criterion 2]
  - [Criterion 3]
  
  Background:
    Given [common precondition for all scenarios]
  
  Scenario: [Scenario name]
    Given [initial context]
    When [action/event]
    Then [expected outcome]
    
  Scenario: [Another scenario name]
    Given [initial context]
    When [different action]
    Then [expected outcome]
```

## Background (Optional)

Use Background: to define common Given steps for all scenarios
Place it before the first Scenario
Keep it short (max 4 steps recommended)
Only include context that is truly shared across ALL scenarios

## Step Keywords

- Given: Initial context, preconditions (past tense)
- When: Actions, events (present tense)
- Then: Expected outcomes, assertions (future/conditional)
- And: Continue the previous step type
- But: Continue with a negation
- *: Use as a bullet-point alternative when listing similar steps

## Best Practices

- One user story per feature file - Keep features focused and manageable
- Write scenarios from the user's perspective - Avoid implementation details
- Make scenarios independent - Each should run in isolation
- Use descriptive names - Both for features and scenarios
- Keep scenarios concise - Aim for 3-7 steps per scenario
- Use Background sparingly - Only for truly common setup
- Prefer Given-When-Then order - It reads more naturally
- Make assertions specific - Test one thing clearly
- Use Scenario Outlines for data variations - Avoid duplicating scenarios
- Tag strategically - For test organization and selective execution