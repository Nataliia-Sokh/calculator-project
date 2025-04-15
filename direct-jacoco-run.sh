#!/bin/bash
# Script to run Cucumber tests directly with JaCoCo agent

# Paths - adjust as needed for your environment
PROJECT_ROOT=$(pwd)
JACOCO_AGENT_PATH=$(find ~/.gradle -name "jacocoagent.jar" | head -1)
CLASSES_PATH="$PROJECT_ROOT/build/classes/java/main"
TEST_CLASSES_PATH="$PROJECT_ROOT/cucumber-tests/build/classes/java/test"
DEPENDENCIES=$(find ~/.gradle -name "*.jar" | grep -v sources | grep -v javadoc | tr '\n' ':')
CUCUMBER_CP="$TEST_CLASSES_PATH:$CLASSES_PATH:$DEPENDENCIES"
JACOCO_DESTFILE="$PROJECT_ROOT/cucumber-tests/build/jacoco/cucumber-direct.exec"

echo "==== Running Cucumber tests directly with JaCoCo ===="
echo "JaCoCo Agent: $JACOCO_AGENT_PATH"
echo "Output file: $JACOCO_DESTFILE"

# Run with JaCoCo agent directly attached
java \
  -javaagent:$JACOCO_AGENT_PATH=destfile=$JACOCO_DESTFILE,includes=com.example.calculator.*,excludes=com.example.calculatorT.*:com.example.coverage.* \
  -cp $CUCUMBER_CP \
  org.junit.platform.console.ConsoleLauncher \
  --scan-classpath \
  --include-engine=cucumber

echo "==== Generating report from direct execution ===="
# Now generate a report from this data
./gradlew -b cucumber-tests/build.gradle jacocoTestReport \
  -PjacocoExecFile=$JACOCO_DESTFILE \
  -PjacocoReportDir="$PROJECT_ROOT/cucumber-tests/build/reports/jacoco/direct"

echo "==== Done ===="
echo "Report location: $PROJECT_ROOT/cucumber-tests/build/reports/jacoco/direct/html/index.html"