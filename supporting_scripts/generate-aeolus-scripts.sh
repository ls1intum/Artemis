#!/usr/bin/env bash

process_windfile() {
  local file="$1"
  local _filename=$(basename "$file")
  if [[ $_filename == *".sh"* ]]; then
      return 0
  fi
  local _dirname=$(dirname "$file")
  dirname $file
  local _tempfile=$(mktemp --tmpdir=".")
  cat "$file" > "$_tempfile"
  echo "metadata:" >> "$_tempfile"
  echo "  id: $_filename" >> "$_tempfile"
  echo "  name: $_filename" >> "$_tempfile"
  echo "  description: $_filename" >> "$_tempfile"
  local _bashname="${_filename%.*}"
  docker run --rm -v "./:/tmp" ghcr.io/ls1intum/aeolus/cli:nightly generate -t cli -i "/tmp/${_tempfile}" > "${_dirname}/${_bashname}.sh"
  rm "$_tempfile"
}

iterate_directory() {
  local directory="$1"

  for language in "$directory"/*; do
    if [ -f "$language" ]; then
      # If it's a file, process it
      process_windfile "$language"
    elif [ -d "$language" ]; then
      # If it's a directory, recursively call the function
      iterate_directory "$language"
    fi
  done
}

main () {
  local directory="./src/main/resources/templates/aeolus"
  iterate_directory "$directory"
}

main "$@"
