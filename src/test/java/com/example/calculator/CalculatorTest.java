package com.example.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Calculator class
 */
public class CalculatorTest {

    private Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }

    @Test
    @DisplayName("Initial result should be zero")
    void initialResultShouldBeZero() {
        assertEquals(0.0, calculator.getResult(), "Initial result should be zero");
    }

    @Test
    @DisplayName("Calculator should add values correctly")
    void shouldAddCorrectly() {
        calculator.perform(Operation.ADD, 5.0);
        assertEquals(5.0, calculator.getResult(), "Should add 5.0 correctly");

        calculator.perform(Operation.ADD, 3.5);
        assertEquals(8.5, calculator.getResult(), "Should add 3.5 correctly");
    }

    @Test
    @DisplayName("Calculator should subtract values correctly")
    void shouldSubtractCorrectly() {
        calculator.perform(Operation.ADD, 10.0);
        calculator.perform(Operation.SUBTRACT, 4.0);
        assertEquals(6.0, calculator.getResult(), "Should subtract 4.0 correctly");
    }

    @Test
    @DisplayName("Calculator should multiply values correctly")
    void shouldMultiplyCorrectly() {
        calculator.perform(Operation.ADD, 4.0);
        calculator.perform(Operation.MULTIPLY, 2.5);
        assertEquals(10.0, calculator.getResult(), "Should multiply by 2.5 correctly");
    }

    @Test
    @DisplayName("Calculator should divide values correctly")
    void shouldDivideCorrectly() {
        calculator.perform(Operation.ADD, 10.0);
        calculator.perform(Operation.DIVIDE, 2.0);
        assertEquals(5.0, calculator.getResult(), "Should divide by 2.0 correctly");
    }

    @Test
    @DisplayName("Calculator should throw exception on division by zero")
    void shouldThrowExceptionOnDivisionByZero() {
        calculator.perform(Operation.ADD, 10.0);

        Exception exception = assertThrows(ArithmeticException.class, () -> {
            calculator.perform(Operation.DIVIDE, 0.0);
        });

        String expectedMessage = "Cannot divide by zero";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage),
                "Exception message should contain '" + expectedMessage + "'");
    }

    @Test
    @DisplayName("Calculator clear should reset result to zero")
    void clearShouldResetResultToZero() {
        calculator.perform(Operation.ADD, 10.0);
        calculator.clear();
        assertEquals(0.0, calculator.getResult(), "Clear should reset result to zero");
    }

    @ParameterizedTest
    @CsvSource({
            "5.0, 7.0, 12.0",
            "10.0, -5.0, 5.0",
            "0.0, 0.0, 0.0",
            "-3.0, -7.0, -10.0"
    })
    @DisplayName("Calculator should handle various addition scenarios")
    void shouldHandleVariousAdditionScenarios(double initial, double addend, double expected) {
        calculator.perform(Operation.ADD, initial);
        calculator.perform(Operation.ADD, addend);
        assertEquals(expected, calculator.getResult(),
                "Should correctly add " + initial + " and " + addend);
    }

    @Test
    @DisplayName("Calculator should support method chaining")
    void shouldSupportMethodChaining() {
        double result = calculator
                .perform(Operation.ADD, 5.0)
                .perform(Operation.MULTIPLY, 2.0)
                .perform(Operation.SUBTRACT, 3.0)
                .getResult();

        assertEquals(7.0, result, "Method chaining should calculate correctly");
    }
}