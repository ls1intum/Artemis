#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests if the compilation succeeds
# ------------------------------

gcc -c -Wall assignment/*.c || error=true
if [ ! $error ]
then
    cd tests
    python3 Tests.py
    exit 0
else
    exit 1
fi
