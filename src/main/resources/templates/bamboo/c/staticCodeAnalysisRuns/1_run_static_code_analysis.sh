#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests if the compilation succeeds
# ------------------------------

# Artemis will look for SCA reports in target, specified in BambooBuildPlanService.java in Artemis
rm -rf target
mkdir target
sudo chown artemis_user:artemis_user target -R

# Navigate into students assignment and save the SCA results in gcc.xml
cd assignment || exit
chmod +x ../tests/Converter.py
make staticAnalysis 2> gcc.txt
python3 ../tests/Converter.py -i gcc.txt -o ../target/gcc.xml
