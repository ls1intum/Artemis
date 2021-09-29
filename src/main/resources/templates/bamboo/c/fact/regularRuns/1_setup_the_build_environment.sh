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
