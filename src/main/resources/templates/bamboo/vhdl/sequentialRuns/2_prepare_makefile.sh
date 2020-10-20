#!/usr/bin/env bash

rm -f assignment/{GNUmakefile, Makefile, makefile}

cp -f tests/Makefile assignment/Makefile || exit 2
