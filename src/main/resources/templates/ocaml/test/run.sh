#!/bin/bash

OUTPUT_FOLDER="test-reports"

eval $(opam env)

if [ ! -d "$OUTPUT_FOLDER" ]; then
    mkdir $OUTPUT_FOLDER
fi
(dune runtest --build-dir=$OUTPUT_FOLDER || exit 0)
