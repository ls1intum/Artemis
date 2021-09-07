#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests if the compilation succeeds
# ------------------------------

sudo gcc -c -Wall assignment/*.c || error=true
if [ ! $error ]
then
    cd tests || exit 0
    python3 Tests.py
    exit 0
else
    exit 1
fi
