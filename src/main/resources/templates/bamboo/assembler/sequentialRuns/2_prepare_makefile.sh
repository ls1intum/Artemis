#!/usr/bin/env bash

rm -f assignment/{GNUmakefile, Makefile, makefile}
rm -f assignment/io.inc

cp -f tests/Makefile assignment/Makefile || exit 2
cp -f tests/io.inc assignment/io.inc || exit 2
