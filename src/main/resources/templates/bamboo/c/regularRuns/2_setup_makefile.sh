#!/usr/bin/env bash

shadowFilePath="../tests/testUtils/c/shadow_exec.c"

foundIncludeDirs=`grep -m 1 'INCLUDEDIRS\s*=' assignment/Makefile`
echo "Include: $foundIncludeDirs"

foundSource=`grep -m 1 'SOURCE\s*=' assignment/Makefile`
foundSource="$foundSource $shadowFilePath"
echo "Source: $foundSource"

rm -f assignment/GNUmakefile
rm -f assignment/makefile

cp -f tests/Makefile assignment/Makefile || exit 2
sed -i "s~INCLUDEDIRS\s*=.*~${foundIncludeDirs}~; s~SOURCE\s*=.*~${foundSource}~" assignment/Makefile