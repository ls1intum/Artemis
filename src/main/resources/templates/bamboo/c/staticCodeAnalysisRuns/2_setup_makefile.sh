#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Setup makefile
# ------------------------------

shadowFilePath="../tests/testUtils/c/shadow_exec.c"

foundIncludeDirs=`grep -m 1 'INCLUDEDIRS\s*=' assignment/Makefile`

foundSource=`grep -m 1 'SOURCE\s*=' assignment/Makefile`
foundSource="$foundSource $shadowFilePath"

rm -f assignment/GNUmakefile
rm -f assignment/makefile

# Use Makefile that prints output formatted in JSON
cp -f tests/MakefileJSON assignment/Makefile || exit 2

