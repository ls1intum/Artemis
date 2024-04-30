#!/usr/bin/env bash

process_windfile() {
  local file="${1}"
  local _regenerate="${2}"
  local _filename=$(basename "$file")
  if [[ $_filename == *".sh"* ]]; then
      return 0
  fi
  local _dirname=$(dirname "$file")
  local _tempfile=$(mktemp --tmpdir=".")
  cat "$file" > "$_tempfile"
  {
    echo "metadata:"
    echo "  id: $_filename"
    echo "  name: $_filename"
    echo "  description: $_filename"
  } >> "$_tempfile"
  local _bashname="${_filename%.*}"
  local _newtempfile=$(mktemp --tmpdir=".")
  docker run --rm -v "./:/tmp" ghcr.io/ls1intum/aeolus/cli:nightly generate -t cli -i "/tmp/${_tempfile}" > "$_newtempfile"
  diff --ignore-blank-lines "${_dirname}/${_bashname}.sh" "${_newtempfile}"
  local _diff=$?
  if [[ $_diff -ne 0 ]]; then
    if [[ "${_regenerate}" -eq 0 ]]; then
        echo "File $file has changed, please rerun the script ($0) locally to regenerate them."
        rm "$_newtempfile"
        rm "$_tempfile"
        exit 1
      fi
    cp "$_newtempfile" "${_dirname}/${_bashname}.sh"
  fi
  rm "$_newtempfile"
  rm "$_tempfile"
}

iterate_directory() {
  local directory="$1"
  local _regenerate="$2"

  for language in "$directory"/*; do
    if [ -f "${language}" ]; then
      process_windfile "${language}" "${_regenerate}"
    elif [ -d "$language" ]; then
      iterate_directory "${language}" "${_regenerate}"
    fi
  done
}

main () {
  local _param="$1"
  local _regenerate=1
  if [[ "${_param}" == "check" ]]; then
    _regenerate=0
  fi
  local directory="./src/main/resources/templates/aeolus"
  iterate_directory "$directory" "${_regenerate}"
}

main "$@"
