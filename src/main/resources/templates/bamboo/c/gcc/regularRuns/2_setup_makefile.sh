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

cp -f tests/Makefile assignment/Makefile || exit 2
sed -i "s~\bINCLUDEDIRS\s*=.*~${foundIncludeDirs}~; s~\bSOURCE\s*=.*~${foundSource}~" assignment/Makefile