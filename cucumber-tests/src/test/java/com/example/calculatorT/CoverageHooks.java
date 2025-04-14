package com.example.calculatorT;

import com.example.coverage.CucumberCoverageAgent;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import io.cucumber.java.hu.De;
import org.aspectj.lang.annotation.Aspect;

/**
 * Cucumber hooks for coverage tracking
 */

public class CoverageHooks {
    
    /**
     * Initialize coverage tracking before any scenarios run
     */
    @BeforeAll
    public static void beforeAllScenarios() {
        System.out.println("BeforeAll hook called");
        CucumberCoverageAgent.initializeCoverage();
    }
    
    /**
     * Before each scenario, set the current scenario name for tracking
     */
    @Before
    public void beforeScenario(Scenario scenario) {
        String scenarioName = scenario.getName();
        System.out.println("Before hook called for scenario: " + scenarioName);
        
        if (scenarioName == null || scenarioName.isEmpty()) {
            // Fallback to ID if name is empty
            scenarioName = "Scenario-" + scenario.getId();
        }
        CucumberCoverageAgent.setCurrentScenario(scenarioName);
    }
    
    /**
     * After all scenarios complete, save the coverage data
     */
    @AfterAll
    public static void afterAllScenarios() {
        System.out.println("AfterAll hook called");
        CucumberCoverageAgent.saveCoverageData();
    }
}
