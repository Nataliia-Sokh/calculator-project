package com.example.calculatorT;

import com.example.calculator.Calculator;
import com.example.calculator.Operation;
import com.example.coverage.CucumberCoverageAgent;

/**
 * Diagnostic tool to check if the CucumberCoverageAgent is properly weaving
 * and tracking Calculator method calls.
 */
public class CoverageAgentDiagnostic {

    public static void main(String[] args) {
        System.out.println("=== CucumberCoverageAgent Diagnostic Tool ===");

        // Initialize the coverage agent
        System.out.println("Initializing CucumberCoverageAgent...");
        CucumberCoverageAgent.initializeCoverage();

        // Set a test scenario name
        CucumberCoverageAgent.setCurrentScenario("diagnostic-test-scenario");
        System.out.println("Set current scenario: diagnostic-test-scenario");

        // Create a Calculator instance and call some methods
        System.out.println("Creating Calculator instance and calling methods...");
        Calculator calculator = new Calculator();
        System.out.println("Initial result: " + calculator.getResult());

        calculator.perform(Operation.ADD, 5.0);
        System.out.println("After ADD 5.0: " + calculator.getResult());

        calculator.perform(Operation.MULTIPLY, 2.0);
        System.out.println("After MULTIPLY 2.0: " + calculator.getResult());

        calculator.clear();
        System.out.println("After CLEAR: " + calculator.getResult());

        // Save coverage data
        System.out.println("Saving coverage data...");
        CucumberCoverageAgent.saveCoverageData();

        // Provide info about where to find the results
        System.out.println("\n=== Diagnostic Complete ===");
        System.out.println("Results should be saved to: build/reports/cucumber-method-coverage.csv");
        System.out.println("And summary at: build/reports/cucumber-coverage-summary.txt");
        System.out.println("\nCheck these files to verify if method tracking is working properly.");
    }
}