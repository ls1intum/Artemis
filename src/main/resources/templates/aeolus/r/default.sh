#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
syntax_check () {
  echo '⚙️ executing syntax_check'
  # Check for syntax errors in the student code
  Rscript -e 'files <- list.files("${studentParentWorkingDirectoryName}", pattern="\\.R$", recursive=TRUE, full.names=TRUE); for (f in files) { tryCatch(parse(f), error=function(e) { cat(conditionMessage(e), "\n"); quit(status=1) }) }'
  COMPILATION_EXIT_CODE=$?

  if [ $COMPILATION_EXIT_CODE -ne 0 ]; then
      exit 1
  fi
}

install () {
  echo '⚙️ executing install'
  Rscript -e 'remotes::install_local()'
}

run_all_tests () {
  echo '⚙️ executing run_all_tests'
  # Run the tests
  Rscript -e 'library("testthat"); options(testthat.output_file = "junit.xml"); test_local(".", reporter = "junit")' || true
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
  bash -c "source ${_script_name} aeolus_sourcing; install"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_all_tests"
}

main "${@}"
