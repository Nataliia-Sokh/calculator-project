package com.example.calculator;

/**
 * A simple calculator class with basic operations
 */
public class Calculator {
    
    private double result;
    
    public Calculator() {
        this.result = 0;
    }
    
    /**
     * Performs the specified operation
     * @param operation The operation to perform
     * @param value The value to use in the operation
     * @return The calculator instance for method chaining
     */
    public Calculator perform(Operation operation, double value) {
        switch (operation) {
            case ADD:
                this.result += value;
                break;
            case SUBTRACT:
                this.result -= value;
                break;
            case MULTIPLY:
                this.result *= value;
                break;
            case DIVIDE:
                if (value == 0) {
                    throw new ArithmeticException("Cannot divide by zero");
                }
                this.result /= value;
                break;
            default:
                throw new UnsupportedOperationException("Operation not supported");
        }
        return this;
    }
    
    /**
     * Resets the calculator result to zero
     * @return The calculator instance for method chaining
     */
    public Calculator clear() {
        this.result = 0;
        return this;
    }
    
    /**
     * Gets the current result
     * @return The current result
     */
    public double getResult() {
        return this.result;
    }
}


