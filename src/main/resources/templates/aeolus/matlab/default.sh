#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
syntax_check () {
  echo '⚙️ executing syntax_check'
  cd "${testWorkingDirectory}"

  sudo mkdir -p test-results
  sudo chown matlab:matlab test-results

  # Check for syntax errors in the student code
  matlab -batch "\
    files = dir(fullfile('..', '${studentParentWorkingDirectoryName}', '**', '*.m')); \
    for k = 1:numel(files), \
      file = fullfile(files(k).folder, files(k).name); \
      if ~isempty(checkcode(file)), exit(1); end; \
    end"
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi

}

test () {
  echo '⚙️ executing test'
  cd "${testWorkingDirectory}"

  sudo rm /etc/sudoers.d/matlab

  # Run the tests
  matlab -batch testRunner || true

}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; syntax_check"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; test"
}

main "${@}"
