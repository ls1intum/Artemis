#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests if the compilation succeeds
# ------------------------------
# Artemis will look for SCA reports in target, specified in BambooBuildPlanService.java in Artemis
mkdir target

cp tests/Converter.py assignment || true

# Go into students assignment and save the SCA results in gcc.xml
cd assignment || exit
chmod +x Converter.py
make staticAnalysis 2> gcc.txt
python3 Converter.py -i gcc.txt -o ../target/gcc.xml
