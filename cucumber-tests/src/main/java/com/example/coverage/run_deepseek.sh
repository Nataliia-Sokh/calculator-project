#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/venv"

# Create venv if it doesn't exist
if [ ! -d "$VENV_DIR" ]; then
  python3 -m venv "$VENV_DIR"
fi

# Activate venv
source "$VENV_DIR/bin/activate"

# Upgrade pip and install dependencies
pip install --upgrade pip
pip install torch numpy transformers tqdm pandas scikit-learn requests

# Run your analysis script with passed arguments
python "$SCRIPT_DIR/run_analysis.py" "$@"
