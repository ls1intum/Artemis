#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests if the compilation succeeds
# ------------------------------

# Artemis will look for SCA reports in target, specified in BambooBuildPlanService.java in Artemis
sudo rm -rf target
sudo mkdir target
sudo chown artemis_user:artemis_user target -R

# Navigate into students assignment and save the SCA results in gcc.xml
cd assignment || exit
sudo make staticAnalysis 2> gcc.txt
sudo chown artemis_user:artemis_user ../tests/Converter.py
python3 ../tests/Converter.py -i gcc.txt -o ../target/gcc.xml

# Give bamboo permissions to delete the target folder after publishing the artifact
sudo chmod -R 777 ${bamboo.working.directory}/target
