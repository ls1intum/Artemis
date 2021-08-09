#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests if the compilation succeeds
# ------------------------------
mkdir target # Artemis will look for reports here
cp Converter.py assignment || true
cd assignment || exit
chmod +x Converter.py

# Recursively check the current directory, showing progress on the screen and logging error messages to a file
# Configure sensitivity by modifying the cppcheck flags with --enable=error|warning|style|performance|portability|information|all path/to/file.c
cppcheck . 2> ../target/cppcheck.xml

# Only currently supported structured output method is JSON, see https://gcc.gnu.org/onlinedocs/gcc-11.1.0/gcc/Diagnostic-Message-Formatting-Options.html
# TODO: Find a way to combine the other sanitizers into one output
make staticAnalysis 2> gcc.json

# Convert the output to xml so we can parse it with the Bamboo plugin
./Converter -i gcc.json -o gcc.xml




