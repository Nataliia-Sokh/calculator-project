#!/bin/bash
# Improved script to run Cucumber tests directly with JaCoCo agent
# This version has better JAR file discovery logic

# Ensure script exits on error
set -e

# Paths - adapt as needed for your environment
PROJECT_ROOT=$(pwd)
MAIN_CLASSES="$PROJECT_ROOT/build/classes/java/main"
TEST_CLASSES="$PROJECT_ROOT/cucumber-tests/build/classes/java/test"
JACOCO_DESTFILE="$PROJECT_ROOT/build/jacoco/cucumber-direct.exec"
REPORT_DIR="$PROJECT_ROOT/build/reports/jacoco/cucumber-direct"

# Function to find JAR files with multiple search paths
find_jar() {
    local jar_name="$1"
    local search_paths=(
        "$PROJECT_ROOT/.gradle"
        "$PROJECT_ROOT/cucumber-tests/.gradle"
        "$HOME/.gradle"
        "$PROJECT_ROOT/gradle"
        "$PROJECT_ROOT/cucumber-tests/build/libs"
        "$PROJECT_ROOT/build/libs"
    )

    for path in "${search_paths[@]}"; do
        if [ -d "$path" ]; then
            local found_jar=$(find "$path" -name "$jar_name" | grep -v "dups" | head -1)
            if [ -n "$found_jar" ]; then
                echo "$found_jar"
                return 0
            fi
        fi
    done

    # Last resort - check if Gradle can tell us
    if command -v ./gradlew &> /dev/null; then
        local gradle_output=$(./gradlew -q printJacocoPath 2>/dev/null || true)
        if [ -f "$gradle_output" ]; then
            echo "$gradle_output"
            return 0
        fi
    fi

    echo ""
    return 1
}

# Find JaCoCo agent jar
echo "Looking for JaCoCo agent JAR..."
JACOCO_AGENT_PATH=$(find_jar "jacocoagent.jar")
if [ -z "$JACOCO_AGENT_PATH" ]; then
    echo "ERROR: Could not find JaCoCo agent JAR"
    echo "Downloading JaCoCo agent JAR directly..."

    # Create temp directory for download
    TEMP_DIR="$PROJECT_ROOT/tmp_jacoco"
    mkdir -p "$TEMP_DIR"

    # Download JaCoCo distribution
    JACOCO_VERSION="0.8.11"
    JACOCO_ZIP="$TEMP_DIR/jacoco-$JACOCO_VERSION.zip"
    JACOCO_URL="https://repo1.maven.org/maven2/org/jacoco/jacoco/$JACOCO_VERSION/jacoco-$JACOCO_VERSION.zip"

    echo "Downloading from $JACOCO_URL"
    if command -v curl &> /dev/null; then
        curl -sL "$JACOCO_URL" -o "$JACOCO_ZIP"
    elif command -v wget &> /dev/null; then
        wget -q "$JACOCO_URL" -O "$JACOCO_ZIP"
    else
        echo "ERROR: Neither curl nor wget is available to download JaCoCo"
        exit 1
    fi

    # Unzip JaCoCo
    unzip -qq -o "$JACOCO_ZIP" -d "$TEMP_DIR"
    JACOCO_AGENT_PATH="$TEMP_DIR/lib/jacocoagent.jar"
    JACOCO_CLI_PATH="$TEMP_DIR/lib/jacococli.jar"

    echo "Downloaded JaCoCo to $JACOCO_AGENT_PATH"
else
    echo "Found JaCoCo agent: $JACOCO_AGENT_PATH"
fi

# Find JaCoCo CLI jar for report generation
echo "Looking for JaCoCo CLI JAR..."
JACOCO_CLI_PATH=$(find_jar "jacococli.jar")
if [ -z "$JACOCO_CLI_PATH" ]; then
    if [ -z "$TEMP_DIR" ]; then
        echo "ERROR: Could not find JaCoCo CLI JAR"
        echo "Downloading JaCoCo CLI JAR directly..."

        # Create temp directory for download if not already created
        TEMP_DIR="$PROJECT_ROOT/tmp_jacoco"
        mkdir -p "$TEMP_DIR"

        # Download JaCoCo distribution if not already downloaded
        JACOCO_VERSION="0.8.11"
        JACOCO_ZIP="$TEMP_DIR/jacoco-$JACOCO_VERSION.zip"
        JACOCO_URL="https://repo1.maven.org/maven2/org/jacoco/jacoco/$JACOCO_VERSION/jacoco-$JACOCO_VERSION.zip"

        echo "Downloading from $JACOCO_URL"
        if command -v curl &> /dev/null; then
            curl -sL "$JACOCO_URL" -o "$JACOCO_ZIP"
        elif command -v wget &> /dev/null; then
            wget -q "$JACOCO_URL" -O "$JACOCO_ZIP"
        else
            echo "ERROR: Neither curl nor wget is available to download JaCoCo"
            exit 1
        fi

        # Unzip JaCoCo
        unzip -qq -o "$JACOCO_ZIP" -d "$TEMP_DIR"
        JACOCO_CLI_PATH="$TEMP_DIR/lib/jacococli.jar"
    else
        JACOCO_CLI_PATH="$TEMP_DIR/lib/jacococli.jar"
    fi

    echo "Using downloaded JaCoCo CLI: $JACOCO_CLI_PATH"
else
    echo "Found JaCoCo CLI: $JACOCO_CLI_PATH"
fi

echo "=== Preparing for direct JaCoCo run ==="
echo "Main classes:     $MAIN_CLASSES"
echo "Test classes:     $TEST_CLASSES"
echo "JaCoCo agent:     $JACOCO_AGENT_PATH"
echo "JaCoCo CLI tool:  $JACOCO_CLI_PATH"
echo "Output file:      $JACOCO_DESTFILE"
echo "Report directory: $REPORT_DIR"

# Create classpath with all dependencies
echo "Building classpath..."
DEPS_CLASSPATH=""

# Find all JARs from .gradle directories
for jar in $(find ~/.gradle -name "*.jar" 2>/dev/null | grep -v "sources\|javadoc" || true); do
    DEPS_CLASSPATH="$DEPS_CLASSPATH:$jar"
done

# Also add any JARs in the build directories
for jar in $(find "$PROJECT_ROOT" -path "*/build/libs/*.jar" 2>/dev/null || true); do
    DEPS_CLASSPATH="$DEPS_CLASSPATH:$jar"
done

FULL_CLASSPATH="$MAIN_CLASSES:$TEST_CLASSES$DEPS_CLASSPATH"

# Ensure destination directory exists
mkdir -p $(dirname "$JACOCO_DESTFILE")
mkdir -p "$REPORT_DIR/html"

echo "=== Running Cucumber tests with JaCoCo agent ==="
java \
  -javaagent:"$JACOCO_AGENT_PATH"=destfile="$JACOCO_DESTFILE",includes=com.example.calculator.*,excludes=com.example.coverage.*:com.example.calculatorT.* \
  -cp "$FULL_CLASSPATH" \
  org.junit.platform.console.ConsoleLauncher \
  --select-package=com.example.calculatorT \
  --include-engine=cucumber

echo "=== Generating JaCoCo report ==="
java -jar "$JACOCO_CLI_PATH" report "$JACOCO_DESTFILE" \
  --classfiles "$MAIN_CLASSES" \
  --sourcefiles "$PROJECT_ROOT/src/main/java" \
  --html "$REPORT_DIR/html" \
  --xml "$REPORT_DIR/report.xml"

echo "=== Done! ==="
echo "HTML report generated at: $REPORT_DIR/html/index.html"