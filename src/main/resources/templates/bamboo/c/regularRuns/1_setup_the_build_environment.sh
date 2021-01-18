#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests
# ------------------------------

# Updating assignment and test-reports ownership...
sudo chown artemis_user:artemis_user assignment/ -R
sudo rm -rf test-reports
sudo mkdir test-reports
sudo chown artemis_user:artemis_user test-reports/ -R

# assignment
cd tests
REQ_FILE=requirements.txt
if [ -f "$REQ_FILE" ]; then
    pip3 install --user -r requirements.txt
else
    echo "$REQ_FILE does not exist"
fi
exit 0
