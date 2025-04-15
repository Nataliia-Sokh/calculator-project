#!/bin/bash
# Script to run the complete coverage reporting suite

# Find the Gradle wrapper script
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
elif [ -f "../gradlew" ]; then
    GRADLE_CMD="../gradlew"
else
    echo "Error: Cannot find Gradle wrapper script (gradlew)"
    echo "Make sure you're running this script from the project root or 'cucumber-tests' directory"
    exit 1
fi

echo "========================================"
echo "    Running Complete Coverage Suite     "
echo "========================================"
echo "Using Gradle wrapper: $GRADLE_CMD"

# Clean previous builds
echo -e "\n[1/5] Cleaning previous builds..."
$GRADLE_CMD clean

# Run unit tests with JaCoCo
echo -e "\n[2/5] Running unit tests..."
$GRADLE_CMD test

# Run Cucumber tests with custom coverage agent
echo -e "\n[3/5] Running Cucumber tests with coverage agent..."
$GRADLE_CMD :cucumber-tests:cucumberTests

# Generate JaCoCo reports
echo -e "\n[4/5] Generating JaCoCo reports (unit, cucumber, aggregated)..."
$GRADLE_CMD jacocoTestReport :cucumber-tests:jacocoCucumberReport :cucumber-tests:jacocoAggregatedReport

# Generate the HTML correlation report
echo -e "\n[5/5] Generating scenario-method correlation report..."
$GRADLE_CMD :cucumber-tests:generateCoverageReport

echo -e "\n========================================"
echo "    Coverage Reports Generated!         "
echo "========================================"
echo -e "\nReports are available at:"
echo "  - Unit Test Coverage:     build/reports/jacoco/test/html/index.html"
echo "  - Cucumber Test Coverage: cucumber-tests/build/reports/jacoco/cucumber/index.html"
echo "  - Aggregated Coverage:    cucumber-tests/build/reports/jacoco/aggregated/index.html"
echo "  - Method-Scenario Correlation: cucumber-tests/build/reports/cucumber-method-coverage.html"
echo -e "\nDone!"