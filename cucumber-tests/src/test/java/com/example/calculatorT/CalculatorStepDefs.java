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
        calculator = new Calculator();
        lastException = null;
    }

    @When("I add {double}")
    public void iAdd(Double number) {
        calculator.perform(Operation.ADD, number);
    }

    @When("I subtract {double}")
    public void iSubtract(Double number) {
        calculator.perform(Operation.SUBTRACT, number);
    }

    @When("I multiply by {double}")
    public void iMultiplyBy(Double number) {
        calculator.perform(Operation.MULTIPLY, number);
    }

    @When("I divide by {double}")
    public void iDivideBy(Double number) {
        try {
            calculator.perform(Operation.DIVIDE, number);
        } catch (Exception e) {
            lastException = e;
        }
    }

    @When("I clear the calculator")
    public void iClearTheCalculator() {
        calculator.clear();
    }

    @Then("the result should be {double}")
    public void theResultShouldBe(Double expected) {
        assertEquals(expected, calculator.getResult(), 0.0001);
    }

    @Then("I should get a divide by zero error")
    public void iShouldGetADivideByZeroError() {
        assertEquals(ArithmeticException.class, lastException.getClass());
    }
}