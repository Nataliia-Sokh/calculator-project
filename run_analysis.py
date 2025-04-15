#!/usr/bin/env python3
import json
import argparse
import subprocess
import re
import sys
import time
import os
from pathlib import Path

def extract_json(response, class_name):
    """
    Highly robust JSON extraction with multiple fallback strategies.
    """
    # Create debug directory
    debug_dir = Path("debug_extractions")
    debug_dir.mkdir(exist_ok=True)

    # Save the full response for debugging
    with open(debug_dir / f"{class_name}_full_response.txt", "w") as f:
        f.write(response)

    # Strategy 1: Extract from code blocks with JSON label
    code_block_matches = re.findall(r'```(?:json)?\s*([\s\S]*?)```', response)

    for i, match in enumerate(code_block_matches):
        try:
            # Clean the potential JSON
            cleaned = match.strip()
            cleaned = cleaned.replace('\u201c', '"').replace('\u201d', '"')  # Smart quotes
            cleaned = re.sub(r"'([^']*)'", r'"\1"', cleaned)  # Single to double quotes
            cleaned = re.sub(r',(\s*[}\]])', r'\1', cleaned)  # Remove trailing commas

            # Try to parse
            parsed = json.loads(cleaned)
            return parsed
        except:
            pass

    # Strategy 2: Find the largest JSON-like structure between braces
    # This uses a regex that can handle nested objects
    brace_pattern = r'{[^{}]*(?:{[^{}]*}[^{}]*)*}'
    matches = list(re.finditer(brace_pattern, response))

    # Sort matches by length (longest first)
    matches.sort(key=lambda match: len(match.group(0)), reverse=True)

    for i, match in enumerate(matches):
        try:
            # Extract and clean the potential JSON
            potential_json = match.group(0)

            # Save for debugging
            with open(debug_dir / f"{class_name}_brace_match_{i}.txt", "w") as f:
                f.write(potential_json)

            # Clean the JSON
            cleaned = potential_json
            cleaned = cleaned.replace('\u201c', '"').replace('\u201d', '"')  # Smart quotes
            cleaned = re.sub(r"'([^']*)'", r'"\1"', cleaned)  # Single to double quotes
            cleaned = re.sub(r',(\s*[}\]])', r'\1', cleaned)  # Remove trailing commas

            # Fix unquoted keys (property: value -> "property": value)
            cleaned = re.sub(r'(\s*)(\w+)(\s*):([^:])', r'\1"\2"\3:\4', cleaned)

            # Try to parse
            parsed = json.loads(cleaned)
            return parsed
        except json.JSONDecodeError as e:
            # Log the specific error for debugging
            with open(debug_dir / f"{class_name}_error_{i}.txt", "w") as f:
                f.write(f"Error: {str(e)}\n\nContent:\n{cleaned}")

    # Strategy 3: Extreme extraction - take everything between first { and last }
    start = response.find('{')
    end = response.rfind('}')

    if start != -1 and end != -1 and end > start:
        try:
            # Extract the largest possible JSON-like structure
            potential_json = response[start:end+1]

            # Save for debugging
            with open(debug_dir / f"{class_name}_extreme_extraction.txt", "w") as f:
                f.write(potential_json)

            # Clean the JSON
            cleaned = potential_json
            cleaned = cleaned.replace('\u201c', '"').replace('\u201d', '"')  # Smart quotes
            cleaned = re.sub(r"'([^']*)'", r'"\1"', cleaned)  # Single to double quotes
            cleaned = re.sub(r',(\s*[}\]])', r'\1', cleaned)  # Remove trailing commas

            # Fix unquoted keys
            cleaned = re.sub(r'(\s*)(\w+)(\s*):([^:])', r'\1"\2"\3:\4', cleaned)

            # Try to parse
            parsed = json.loads(cleaned)
            return parsed
        except:
            pass

    # If we get here, all extraction methods failed
    return None

def create_detailed_test_prompt(class_name, file_path, methods, coverage_data=None):
    """
    Create a prompt asking for detailed, method-level test scenarios.
    But ensuring the output is compatible with the Java analyzer.
    """
    # Prepare methods info with limiting for very large classes
    if len(methods) > 10:
        # Process only the first 10 methods for large classes
        methods_for_prompt = methods[:10]
        method_suffix = f" (showing 10 of {len(methods)} methods)"
    else:
        methods_for_prompt = methods
        method_suffix = ""

    # Format methods for better readability
    methods_json = json.dumps(methods_for_prompt, indent=2)

    # Add coverage data if available
    coverage_info = ""
    if coverage_data and class_name in coverage_data:
        coverage_info = f"""
Current code coverage information:
{json.dumps(coverage_data[class_name], indent=2)}

Please identify testing gaps based on this coverage data.
"""

    # Create the prompt - now with the expected format for DeepSeekAnalyzer.java
    prompt = f"""
I need detailed test scenarios for a Java class. Analyze the code and suggest SPECIFIC test cases.

Class: '{class_name}' from '{file_path}'{method_suffix}

Methods:
{methods_json}

{coverage_info}

Please return a VALID JSON object with this structure:
{{
  "className": "{class_name}",
  "complexity": 7,
  "testPriority": 8,
  "suggestedTestScenarios": [
    "Given specific input X, verify method returns Y",
    "Test with boundary condition Z to verify behavior W",
    "Verify exception handling when invalid input is provided"
  ]
}}

Your test scenarios must be specific to the class functionality, including:
1. Concrete inputs and expected outputs
2. Edge cases and boundary conditions
3. Error handling scenarios
4. Complex logical branches

DO NOT use generic placeholders like "X", "Y", "Z" in your actual scenarios.
DO NOT return your response in a code block or with backticks.
The response MUST be valid JSON and start with {{
"""

    return prompt

def process_methods(methods, max_methods=10):
    """
    Process methods to reduce complexity for large classes.
    This helps prevent timeouts and JSON parsing errors.
    """
    if len(methods) <= max_methods:
        return methods

    # Take a representative sample focusing on different method types
    selected = []

    # Always include constructors if present
    constructors = [m for m in methods if m["name"].endswith("<init>") or m["name"] == methods[0]["className"]]
    selected.extend(constructors[:2])  # At most 2 constructors

    # Select methods with different return types (if available)
    return_types = {}
    for method in methods:
        signature = method.get("signature", "")
        return_type = signature.split(" ")[0] if " " in signature else "void"
        if return_type not in return_types and len(return_types) < 3:
            return_types[return_type] = method
            selected.append(method)

    # Add methods with complex signatures (likely to have edge cases)
    complex_methods = []
    for method in methods:
        signature = method.get("signature", "")
        if "Exception" in signature or signature.count(",") > 1:
            complex_methods.append(method)
    selected.extend(complex_methods[:3])  # Add up to 3 complex methods

    # Fill the rest with a mix of first, middle and last methods
    remaining_slots = max_methods - len(selected)
    if remaining_slots > 0:
        indices = []
        # Add some from the beginning
        indices.extend(range(min(3, remaining_slots)))

        # Add some from the middle if enough space
        if remaining_slots > 3:
            middle_idx = len(methods) // 2
            indices.extend(range(middle_idx, min(middle_idx + remaining_slots - 3, len(methods))))

        # Add some from the end if still space
        if len(indices) < remaining_slots:
            indices.extend(range(len(methods) - (remaining_slots - len(indices)), len(methods)))

        # Add unique methods not already selected
        for idx in indices:
            if idx < len(methods) and methods[idx] not in selected:
                selected.append(methods[idx])
                if len(selected) >= max_methods:
                    break

    return selected[:max_methods]

def create_fallback_test_recommendations(class_name, methods):
    """
    Create a fallback response with reasonable test recommendations.
    This is used when the model fails to return valid JSON.
    Format compatible with DeepSeekAnalyzer.java
    """
    # Extract method names for creating specific test scenarios
    method_names = []
    for method in methods[:5]:
        method_names.append(method.get("name", "unknown"))

    # Create scenarios based on method names and patterns
    scenarios = []

    # Add method-specific scenarios
    for method_name in method_names:
        # Basic positive test
        scenarios.append(f"Test {method_name}() with valid inputs to verify correct functionality")

        # Add some edge case tests
        scenarios.append(f"Test {method_name}() with boundary values to verify proper handling")

        # Add an error handling test
        scenarios.append(f"Test {method_name}() with invalid inputs to verify error handling")

    # General scenarios if we have few method-specific ones
    if len(scenarios) < 5:
        scenarios.append("Test class initialization with valid parameters")
        scenarios.append("Test class behavior with null or empty inputs")
        scenarios.append("Test exception handling for all error conditions")
        scenarios.append("Verify thread safety for concurrent operations")

    # Return in the format expected by DeepSeekAnalyzer.java
    return {
        "className": class_name,
        "complexity": 5,
        "testPriority": 6,
        "suggestedTestScenarios": scenarios
    }

def call_model(class_name, file_path, methods, model, timeout=180, max_attempts=2):
    """
    Call the model to generate detailed test recommendations.
    """
    for attempt in range(max_attempts):
        try:
            # Create the test-focused prompt
            prompt = create_detailed_test_prompt(class_name, file_path, methods)

            # Add stronger JSON instructions on retry
            if attempt > 0:
                prompt = f"""
CRITICAL: You MUST return a valid JSON object ONLY - no explanations, no markdown.
The first character must be "{{" and the last character must be "}}".

{prompt}

REMEMBER: VALID JSON ONLY. NO TEXT BEFORE OR AFTER THE JSON OBJECT.
"""

            # Call the model
            print(f"🔍 Analyzing tests for {class_name} (attempt {attempt+1}/{max_attempts})")
            result = subprocess.run(
                ["ollama", "run", model],
                input=prompt.encode("utf-8"),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=timeout,
            )

            # Get the response
            response = result.stdout.decode("utf-8").strip()

            # Try to extract JSON
            parsed = extract_json(response, f"{class_name}_detailed_test_{attempt+1}")
            if parsed:
                # Ensure the response has the expected structure for DeepSeekAnalyzer.java
                if "className" not in parsed or "suggestedTestScenarios" not in parsed:
                    print("⚠️ Response missing required fields")
                    continue

                return parsed
            else:
                print(f"⚠️ Attempt {attempt+1} failed to extract valid JSON.")

                # Try reducing methods for next attempt
                if len(methods) > 5 and attempt < max_attempts - 1:
                    methods = methods[:5]  # Drastically reduce for next attempt
                    print(f"📉 Reducing to 5 methods for next attempt")
        except subprocess.TimeoutExpired:
            print(f"⏱ Timeout on attempt {attempt+1}")
            # Reduce methods by half for next attempt
            if len(methods) > 3 and attempt < max_attempts - 1:
                methods = methods[:len(methods)//2]
                print(f"📉 Reducing to {len(methods)} methods due to timeout")
        except Exception as e:
            print(f"❌ Error during model call: {e}")

    # If we get here, all attempts failed
    print("⚠️ Creating fallback test recommendations")
    return create_fallback_test_recommendations(class_name, methods)

def load_coverage_data(coverage_file):
    """
    Load existing code coverage data if available.
    """
    try:
        if coverage_file and os.path.exists(coverage_file):
            with open(coverage_file, 'r') as f:
                return json.load(f)
    except Exception as e:
        print(f"⚠️ Warning: Could not load coverage data: {e}")
    return None

def main():
    parser = argparse.ArgumentParser(description="Generate detailed test recommendations with coverage gap analysis")
    parser.add_argument("--model", required=True, help="Ollama model name to use")
    parser.add_argument("--input", required=True, help="Input JSON file with class information")
    parser.add_argument("--output", required=True, help="Output JSON file for results")
    parser.add_argument("--coverage", help="Optional JSON file with existing code coverage data")
    parser.add_argument("--timeout", type=int, default=180, help="Timeout in seconds per model call")
    parser.add_argument("--max-methods", type=int, default=10, help="Maximum methods to analyze per class")
    args = parser.parse_args()

    # Create necessary directories
    os.makedirs("debug_extractions", exist_ok=True)

    # Load coverage data if provided
    coverage_data = load_coverage_data(args.coverage)

    # Load input data
    with open(args.input) as f:
        data = json.load(f)

    # Prepare output structure - USING recommendations KEY TO MATCH DeepSeekAnalyzer.java
    output = {
        "model": args.model,
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "recommendations": []  # This matches what DeepSeekAnalyzer.java expects
    }

    # Track statistics
    stats = {
        "total_classes": len(data["classes"]),
        "successful": 0,
        "failed": 0,
        "processing_time": 0
    }

    start_time = time.time()

    # Process each class
    for idx, clazz in enumerate(data["classes"]):
        class_name = clazz["className"]
        file_path = clazz["filePath"]
        all_methods = clazz["methods"]

        # Smart method selection for large classes
        methods = process_methods(all_methods, args.max_methods)

        # Print progress
        print(f"🔍 [{idx+1}/{stats['total_classes']}] Analyzing tests for: {class_name} ({len(methods)}/{len(all_methods)} methods)")

        # Call the model to get test recommendations
        class_start_time = time.time()
        test_recommendations = call_model(
            class_name,
            file_path,
            methods,
            args.model,
            timeout=args.timeout
        )
        class_time = time.time() - class_start_time

        # Add to output
        if test_recommendations:
            output["recommendations"].append(test_recommendations)
            stats["successful"] += 1
            print(f"✅ Generated test recommendations for {class_name} in {class_time:.2f}s")
        else:
            # This should never happen due to fallback, but just in case
            fallback = create_fallback_test_recommendations(class_name, methods)
            output["recommendations"].append(fallback)
            stats["failed"] += 1
            print(f"⚠️ Using fallback recommendations for {class_name}")

    # Calculate total time
    stats["processing_time"] = time.time() - start_time

    # Save the output
    with open(args.output, "w") as out:
        json.dump(output, out, indent=2)
    print(f"✅ Saved detailed test recommendations to {args.output}")

    # Print summary
    success_rate = (stats["successful"] / stats["total_classes"]) * 100 if stats["total_classes"] > 0 else 0
    print(f"\n{'='*60}")
    print(f"SUMMARY: Processed {stats['total_classes']} classes in {stats['processing_time']:.2f}s")
    print(f"SUCCESS: {stats['successful']} classes ({success_rate:.1f}%)")
    print(f"FAILED: {stats['failed']} classes")
    print(f"{'='*60}")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n⚠️ Process interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Fatal error: {str(e)}")
        sys.exit(1)