#!/bin/bash
set -e

# Parameters
SRC_DIR="src/main/java"
OUT_DIR="analyzer-output"
MODEL="deepseek-local:latest"

# Step 1: Run the Java analyzer (assumes it's already compiled and on classpath)
echo "=== Running DeepSeekAnalyzer ==="
java -cp build/libs/* com.example.coverage.DeepSeekAnalyzer "$SRC_DIR" "$OUT_DIR" "$MODEL"

# Step 2: Run the enhanced DeepSeek Python analysis
echo "=== Running DeepSeek model analysis ==="
./run_deepseek.sh --model "$MODEL" \
  --input "$OUT_DIR/deepseek-input.json" \
  --output "$OUT_DIR/results.json"

echo "✅ Analysis complete. See:"
echo " - $OUT_DIR/results.json (structured)"
echo " - $OUT_DIR/test-recommendations.html (visual report)"
