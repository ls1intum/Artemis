#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests
# ------------------------------

rm -f assignment/GNUmakefile
rm -f assignment/Makefile
cp -f tests/Makefile assignment/Makefile || exit 2

cd tests
python3 Tests.py
rm Tests.py
rm -rf ./tests
exit 0
