package com.example.calculatorT;

import com.example.calculator.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Step definitions for calculator feature
 */
public class CalculatorStepDefs {

    private Calculator calculator;
    private Exception lastException;

    @Given("I have a calculator")
    public void iHaveACalculator() {
        System.out.println("[DEBUG] Creating new Calculator instance");
        calculator = new Calculator();
        lastException = null;
    }

    @When("I add {double}")
    public void iAdd(Double number) {
        System.out.println("[DEBUG] Adding: " + number);
        calculator.perform(Operation.ADD, number);
    }

    @When("I subtract {double}")
    public void iSubtract(Double number) {
        System.out.println("[DEBUG] Subtracting: " + number);
        calculator.perform(Operation.SUBTRACT, number);
    }

    @When("I multiply by {double}")
    public void iMultiplyBy(Double number) {
        System.out.println("[DEBUG] Multiplying by: " + number);
        calculator.perform(Operation.MULTIPLY, number);
    }

    @When("I divide by {double}")
    public void iDivideBy(Double number) {
        System.out.println("[DEBUG] Dividing by: " + number);
        try {
            calculator.perform(Operation.DIVIDE, number);
        } catch (Exception e) {
            System.out.println("[DEBUG] Exception caught: " + e.getMessage());
            lastException = e;
        }
    }

    @When("I clear the calculator")
    public void iClearTheCalculator() {
        System.out.println("[DEBUG] Clearing calculator");
        calculator.clear();
    }

    @Then("the result should be {double}")
    public void theResultShouldBe(Double expected) {
        double result = calculator.getResult();
        System.out.println("[DEBUG] Checking result: expected " + expected + ", actual " + result);
        assertEquals(expected, result, 0.0001);
    }

    @Then("I should get a divide by zero error")
    public void iShouldGetADivideByZeroError() {
        System.out.println("[DEBUG] Checking for divide by zero error");
        assertEquals(ArithmeticException.class, lastException.getClass());
    }
}