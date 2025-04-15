package com.example.calculatorT;

import com.example.coverage.CucumberCoverageAgent;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;

/**
 * Cucumber hooks for coverage tracking
 */
public class CoverageHooks {

    /**
     * Initialize coverage tracking before any scenarios run
     */
    @BeforeAll
    public static void beforeAllScenarios() {
        System.out.println("==================================");
        System.out.println("BeforeAll hook called - Initializing CucumberCoverageAgent");
        CucumberCoverageAgent.initializeCoverage();
        System.out.println("CucumberCoverageAgent initialized");
        System.out.println("==================================");
    }

    /**
     * Before each scenario, set the current scenario name for tracking
     */
    @Before
    public void beforeScenario(Scenario scenario) {
        String scenarioName = scenario.getName();
        System.out.println("----------------------------------");
        System.out.println("Before hook called for scenario: " + scenarioName);

        if (scenarioName == null || scenarioName.isEmpty()) {
            // Fallback to ID if name is empty
            scenarioName = "Scenario-" + scenario.getId();
        }
        CucumberCoverageAgent.setCurrentScenario(scenarioName);
        System.out.println("Current scenario set to: " + scenarioName);
        System.out.println("----------------------------------");
    }

    /**
     * After each scenario, print some status info
     */
    @After
    public void afterScenario(Scenario scenario) {
        System.out.println("----------------------------------");
        System.out.println("After hook called for scenario: " + scenario.getName());
        System.out.println("Scenario status: " + (scenario.isFailed() ? "FAILED" : "PASSED"));
        System.out.println("----------------------------------");
    }

    /**
     * After all scenarios complete, save the coverage data
     */
    @AfterAll
    public static void afterAllScenarios() {
        System.out.println("==================================");
        System.out.println("AfterAll hook called - Saving coverage data");
        CucumberCoverageAgent.saveCoverageData();
        System.out.println("Coverage data saved");
        System.out.println("==================================");
    }
}