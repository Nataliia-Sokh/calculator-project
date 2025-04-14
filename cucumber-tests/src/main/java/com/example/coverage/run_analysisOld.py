#!/usr/bin/env python3
# run_analysis.py

import argparse
import json
import os
import sys
import time
import requests
from typing import Dict, List, Any

def load_ollama_model(model_name):
    """Connect to the Ollama API."""
    print(f"Connecting to Ollama model: {model_name}")
    
    # Test connection to Ollama
    try:
        response = requests.get("http://localhost:11434/api/tags")
        if response.status_code == 200:
            models = response.json().get("models", [])
            model_names = [model.get("name") for model in models]
            
            if not any(model_name in name for name in model_names):
                print(f"Warning: Model '{model_name}' not explicitly found in Ollama. Available models: {model_names}")
                
            print(f"Successfully connected to Ollama API")
            return True
    except Exception as e:
        print(f"Error connecting to Ollama API: {e}")
        print("Make sure Ollama is running on http://localhost:11434")
        return False

def generate_with_ollama(prompt, model_name, max_length=1024, temperature=0.1):
    """Generate text using the Ollama API."""
    try:
        response = requests.post(
            "http://localhost:11434/api/generate",
            json={
                "model": model_name,
                "prompt": prompt,
                "options": {
                    "temperature": temperature,
                    "num_predict": max_length
                }
            }
        )
        
        if response.status_code == 200:
            return [{"generated_text": response.json().get("response", "")}]
        else:
            print(f"Error from Ollama API: {response.status_code}, {response.text}")
            return [{"generated_text": "Error generating response"}]
    except Exception as e:
        print(f"Exception when calling Ollama API: {e}")
        return [{"generated_text": "Error generating response"}]

def analyze_class(model_name, class_info: Dict[str, Any]) -> Dict[str, Any]:
    """Analyze a single class using Ollama."""
    class_name = class_info["className"]
    methods = class_info["methods"]

    print(f"Analyzing class: {class_name} with {len(methods)} methods")

    # Construct prompt for the model
    prompt = f"""
You are an expert software engineer responsible for ensuring adequate test coverage.
Analyze this Java class and suggest test scenarios.

Class name: {class_name}
Methods: {', '.join(methods)}

Based on the class name and methods, please:
1. Estimate the complexity of this class (scale 1-10)
2. Determine the priority for testing (scale 1-10)
3. Suggest at least 3 specific test scenarios that should be implemented

Format your response as JSON with the following structure:
{{
  "complexity": <number>,
  "testPriority": <number>,
  "suggestedTestScenarios": ["scenario1", "scenario2", "scenario3"]
}}
"""

    # Generate response using Ollama
    response = generate_with_ollama(prompt, model_name, max_length=1024, temperature=0.1)

    # Extract JSON from response
    try:
        # The response might include additional text before/after the JSON
        # We need to extract just the JSON part
        response_text = response[0]['generated_text']

        # Find the JSON part within the triple backticks if present
        json_start = response_text.find("{")
        json_end = response_text.rfind("}")

        if json_start >= 0 and json_end >= 0:
            json_str = response_text[json_start:json_end+1]
            result = json.loads(json_str)

            # Add class name to result
            result["className"] = class_name
            return result
        else:
            raise ValueError("Could not find JSON in response")

    except Exception as e:
        print(f"Error parsing response for {class_name}: {e}")
        # Return fallback response
        return {
            "className": class_name,
            "complexity": 5.0,
            "testPriority": 5.0,
            "suggestedTestScenarios": [
                f"Test basic functionality of {class_name}",
                f"Test edge cases for {class_name}",
                f"Test error handling in {class_name}"
            ]
        }

def main():
    parser = argparse.ArgumentParser(description='Ollama Code Analysis')
    parser.add_argument('--model', type=str, default='deepseek-local:latest',
                        help='Ollama model to use')
    parser.add_argument('--input', type=str, required=True,
                        help='JSON input file with class information')
    parser.add_argument('--output', type=str, required=True,
                        help='JSON output file for analysis results')

    args = parser.parse_args()

    # Connect to Ollama model
    if not load_ollama_model(args.model):
        sys.exit(1)

    # Load input file
    try:
        with open(args.input, 'r') as f:
            input_data = json.load(f)

        classes = input_data.get("classes", [])
        print(f"Loaded {len(classes)} classes for analysis")
    except Exception as e:
        print(f"Error loading input file: {e}")
        sys.exit(1)

    # Analyze each class
    recommendations = []
    for class_info in classes:
        result = analyze_class(args.model, class_info)
        recommendations.append(result)

        # Brief pause to avoid overloading
        time.sleep(0.5)

    # Write output
    try:
        output = {
            "recommendations": recommendations
        }

        with open(args.output, 'w') as f:
            json.dump(output, f, indent=2)

        print(f"Analysis complete. Results written to {args.output}")
    except Exception as e:
        print(f"Error writing output file: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()