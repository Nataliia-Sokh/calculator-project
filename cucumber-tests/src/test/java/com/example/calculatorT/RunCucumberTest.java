package com.example.calculatorT;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME;

/**
 * Cucumber test runner with explicit configuration to fix test discovery issues
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.calculatorT")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:build/reports/cucumber/report.html, json:build/reports/cucumber/report.json")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "classpath:features")
public class RunCucumberTest {
    // Empty class - the annotations do all the work
    // Log something to verify the test runner class is loaded
    static {
        System.out.println("RunCucumberTest class initialized - Cucumber test discovery should start now");
    }

}