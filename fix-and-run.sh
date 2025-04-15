#!/bin/bash
# Master script to fix coverage issues and run everything

set -e  # Exit on error

# Find gradle wrapper
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    echo "ERROR: Could not find gradle wrapper (gradlew)"
    exit 1
fi

echo "=== Coverage Fix and Run Script ==="
echo "This script will fix both JaCoCo and CucumberCoverageAgent issues"

echo "Step 1: Copying updated AspectJ configuration..."
mkdir -p cucumber-tests/build/resources/main/META-INF
mkdir -p cucumber-tests/build/resources/test/META-INF
cp cucumber-tests/src/test/resources/META-INF/aop.xml cucumber-tests/build/resources/main/META-INF/
cp cucumber-tests/src/test/resources/META-INF/aop.xml cucumber-tests/build/resources/test/META-INF/

echo "Step 2: Cleaning previous builds..."
$GRADLE_CMD clean

echo "Step 3: Compiling code..."
$GRADLE_CMD compileJava compileTestJava

echo "Step 4: Running diagnostic to verify CucumberCoverageAgent..."
$GRADLE_CMD :cucumber-tests:diagnoseCoverageAgent

echo "Step 5: Running Cucumber tests for method tracking (with AspectJ)..."
$GRADLE_CMD :cucumber-tests:cucumberTests

echo "Step 6: Verifying cucumber-method-coverage.csv..."
if [ -f "build/reports/cucumber-method-coverage.csv" ]; then
    echo "✅ Method tracking file found"
else
    echo "WARNING: Method tracking file not found. Checking alternate location..."
    if [ -f "cucumber-tests/build/reports/cucumber-method-coverage.csv" ]; then
        echo "✅ Method tracking file found in cucumber-tests directory"
        # Copy to expected location
        mkdir -p build/reports
        cp cucumber-tests/build/reports/cucumber-method-coverage.csv build/reports/
    else
        echo "❌ Method tracking file not found. There may still be issues with AspectJ."
    fi
fi

echo "Step 7: Generating coverage HTML report..."
$GRADLE_CMD :cucumber-tests:generateCoverageReport

echo "Step 8: Running Cucumber tests for JaCoCo coverage..."
$GRADLE_CMD :cucumber-tests:cucumberWithCoverage

echo "Step 9: Generating JaCoCo reports..."
$GRADLE_CMD :cucumber-tests:jacocoCucumberReport :cucumber-tests:jacocoAggregatedReport

echo "=== All Done! ==="
echo "Check these report locations:"
echo "- Method tracking: build/reports/cucumber-method-coverage.html"
echo "- JaCoCo coverage: cucumber-tests/build/reports/jacoco/cucumber/html/index.html"
echo "- Aggregated coverage: cucumber-tests/build/reports/jacoco/aggregated/html/index.html"