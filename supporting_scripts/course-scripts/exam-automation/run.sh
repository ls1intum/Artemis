#!/bin/bash

# Artemis Exam Automation Runner Script
# This script creates or reuses an existing virtual environment and runs the exam workflow

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/venv"
PYTHON_CMD="python3"

echo -e "${BLUE}=== Artemis Exam Automation Runner ===${NC}"
echo -e "${BLUE}Script directory: $SCRIPT_DIR${NC}"

# Check if Python 3 is available
if ! command -v $PYTHON_CMD &> /dev/null; then
    echo -e "${RED}Error: Python 3 is not installed or not in PATH${NC}"
    echo "Please install Python 3.8 or higher and try again"
    exit 1
fi

# Check Python version
PYTHON_VERSION=$($PYTHON_CMD --version 2>&1 | cut -d' ' -f2 | cut -d'.' -f1,2)
REQUIRED_VERSION="3.8"

if [ "$(printf '%s\n' "$REQUIRED_VERSION" "$PYTHON_VERSION" | sort -V | head -n1)" != "$REQUIRED_VERSION" ]; then
    echo -e "${RED}Error: Python $PYTHON_VERSION found, but Python $REQUIRED_VERSION or higher is required${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Python $PYTHON_VERSION found${NC}"

# Create virtual environment if it doesn't exist
if [ ! -d "$VENV_DIR" ]; then
    echo -e "${YELLOW}Creating virtual environment...${NC}"
    $PYTHON_CMD -m venv "$VENV_DIR"
    echo -e "${GREEN}✓ Virtual environment created at $VENV_DIR${NC}"
else
    echo -e "${GREEN}✓ Using existing virtual environment at $VENV_DIR${NC}"
fi

# Activate virtual environment
echo -e "${YELLOW}Activating virtual environment...${NC}"
source "$VENV_DIR/bin/activate"

# Install requirements
echo -e "${YELLOW}Installing requirements...${NC}"
if [ -f "$SCRIPT_DIR/requirements.txt" ]; then
    pip install -r "$SCRIPT_DIR/requirements.txt" > /dev/null 2>&1
    echo -e "${GREEN}✓ Requirements installed${NC}"
else
    echo -e "${YELLOW}Warning: requirements.txt not found, skipping dependency installation${NC}"
fi

# Check if config.ini exists
if [ ! -f "$SCRIPT_DIR/config.ini" ]; then
    echo -e "${RED}Error: config.ini not found in $SCRIPT_DIR${NC}"
    echo "Please ensure config.ini is properly configured before running the exam workflow"
    exit 1
fi

echo -e "${GREEN}✓ Configuration file found${NC}"

# Run the exam workflow
echo -e "${BLUE}=== Starting Exam Workflow ===${NC}"
echo -e "${YELLOW}Running exam_workflow.py...${NC}"

if [ -f "$SCRIPT_DIR/exam_workflow.py" ]; then
    $PYTHON_CMD "$SCRIPT_DIR/exam_workflow.py"
    EXIT_CODE=$?

    if [ $EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}=== Exam Workflow Completed Successfully ===${NC}"
    else
        echo -e "${RED}=== Exam Workflow Failed with exit code $EXIT_CODE ===${NC}"
        exit $EXIT_CODE
    fi
else
    echo -e "${RED}Error: exam_workflow.py not found in $SCRIPT_DIR${NC}"
    exit 1
fi

echo -e "${BLUE}=== Script Execution Complete ===${NC}"