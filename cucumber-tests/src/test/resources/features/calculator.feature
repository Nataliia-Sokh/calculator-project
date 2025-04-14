Feature: Calculator operations
  As a user
  I want to perform basic arithmetic operations
  So that I can calculate values

  Scenario: Adding numbers
    Given I have a calculator
    When I add 5
    And I add 10
    Then the result should be 15

  Scenario: Subtracting numbers
    Given I have a calculator
    When I add 10
    And I subtract 5
    Then the result should be 5

  Scenario: Multiplying numbers
    Given I have a calculator
    When I add 5
    And I multiply by 3
    Then the result should be 15

  Scenario: Dividing numbers
    Given I have a calculator
    When I add 10
    And I divide by 2
    Then the result should be 5

  Scenario: Division by zero
    Given I have a calculator
    When I add 10
    And I divide by 0
    Then I should get a divide by zero error

  Scenario: Complex calculation
    Given I have a calculator
    When I add 10
    And I multiply by 2
    And I subtract 5
    And I divide by 3
    Then the result should be 5

  Scenario: Clearing the calculator
    Given I have a calculator
    When I add 10
    And I clear the calculator
    Then the result should be 0