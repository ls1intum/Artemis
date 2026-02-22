#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
install () {
  echo '⚙️ executing install'
  Rscript -e 'remotes::install_local()'
}

static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  Rscript -e 'lints <- lintr::lint_package("./assignment"); lintr::sarif_output(lints, "lintr_results.sarif")'
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
  bash -c "source ${_script_name} aeolus_sourcing; install"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; static_code_analysis"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; run_all_tests"
}

main "${@}"
