#!/usr/bin/env bash

foundIncludeDirs=`grep -m 1 'INCLUDEDIRS\s*=' assignment/Makefile`
echo "Include: $foundIncludeDirs"

foundSource=`grep -m 1 'SOURCE\s*=' assignment/Makefile`
echo "Source: $foundSource"

sed -i "s/INCLUDEDIRS\s*=.*/$foundIncludeDirs/; s/SOURCE\s*=.*/$foundSource/" assignment/Makefile