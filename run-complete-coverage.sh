#!/bin/bash
# Master script to run both AspectJ method tracking and JaCoCo code coverage

set -e  # Exit on error

# Find gradle wrapper
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    echo "ERROR: Could not find gradle wrapper (gradlew)"
    exit 1
fi

echo "======================================================="
echo "     Complete Coverage Workflow - Both Systems         "
echo "======================================================="

echo "Step 1: Cleaning previous builds..."
$GRADLE_CMD clean

echo "Step 2: Setting up AspectJ configuration..."
mkdir -p cucumber-tests/build/resources/main/META-INF
mkdir -p cucumber-tests/build/resources/test/META-INF
cp cucumber-tests/src/test/resources/META-INF/aop.xml cucumber-tests/build/resources/main/META-INF/
cp cucumber-tests/src/test/resources/META-INF/aop.xml cucumber-tests/build/resources/test/META-INF/

echo "Step 3: Running unit tests with JaCoCo..."
$GRADLE_CMD test

echo "Step 4: Running diagnostic to verify CucumberCoverageAgent..."
$GRADLE_CMD :cucumber-tests:diagnoseCoverageAgent

echo "Step 5: Running Cucumber tests with AspectJ for method tracking..."
$GRADLE_CMD :cucumber-tests:cucumberTests

echo "Step 6: Generating method tracking HTML report..."
if [ -f "build/reports/cucumber-method-coverage.csv" ] || [ -f "cucumber-tests/build/reports/cucumber-method-coverage.csv" ]; then
    $GRADLE_CMD :cucumber-tests:generateCoverageReport
    echo "✅ Method tracking report generated"
else
    echo "❌ Method tracking CSV file not found. AspectJ weaving may not be working correctly."
    echo "Trying to run the diagnostic tool again to see more details..."
    $GRADLE_CMD :cucumber-tests:diagnoseCoverageAgent
fi

echo "Step 7: Running Cucumber tests with JaCoCo for code coverage..."
$GRADLE_CMD :cucumber-tests:cucumberWithCoverage

echo "Step 8: Generating JaCoCo reports..."
$GRADLE_CMD :cucumber-tests:jacocoCucumberReport :cucumber-tests:jacocoAggregatedReport

echo "Step 9: Checking JaCoCo report validity..."
if grep -q "Total.*[1-9][0-9]*%" cucumber-tests/build/reports/jacoco/cucumber/html/index.html 2>/dev/null; then
    echo "✅ JaCoCo reports generated successfully with coverage data"
else
    echo "⚠️ JaCoCo reports may not contain valid coverage data, trying direct approach..."
    bash direct-jacoco-run.sh
fi

echo "======================================================="
echo "                    All Done!                          "
echo "======================================================="
echo "Check these report locations:"
echo ""
echo "AspectJ Method Tracking Reports:"
echo "- Method-scenario mapping: build/reports/cucumber-method-coverage.html"
echo "- Method tracking summary: build/reports/cucumber-coverage-summary.txt"
echo ""
echo "JaCoCo Code Coverage Reports:"
echo "- Unit test coverage:      build/reports/jacoco/test/html/index.html"
echo "- Cucumber test coverage:  cucumber-tests/build/reports/jacoco/cucumber/html/index.html"
echo "- Aggregated coverage:     cucumber-tests/build/reports/jacoco/aggregated/html/index.html"
echo "- Direct JaCoCo approach:  build/reports/jacoco/cucumber-direct/html/index.html (if used)"
echo ""
echo "Both systems should now be working correctly!"