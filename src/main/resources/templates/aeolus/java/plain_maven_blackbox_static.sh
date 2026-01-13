#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
build () {
  echo '⚙️ executing build'
  mvn -B clean compile
}

main_method_checker () {
  echo '⚙️ executing main_method_checker'
  main_checker_output=$(pipeline-helper main-method -s target/classes)

  line_count="$(echo "$main_checker_output" | wc -l)"
  main_class="$(echo "$main_checker_output" | head -n1)"

  if [ "${line_count}" -eq 2 ]; then
    echo main method found in file: "${main_class}"
    sed -i "s#MAIN_CLASS#${main_class}#" testsuite/config/default.exp
  else
    echo "no main method found. quitting the test run."
    exit 1
  fi
}

custom_checkers () {
  echo '⚙️ executing custom_checkers'
  # all java files in the assignment folder should have maximal line length 80
  pipeline-helper line-length -l 80 -s ${studentParentWorkingDirectoryName}/ -e java
  # checks that the file exists and is not empty for non gui programs
  pipeline-helper file-exists ${studentParentWorkingDirectoryName}/Tests.txt
}

replace_script_variables () {
  echo '⚙️ executing replace_script_variables'
  local JAVA_FLAGS=""
  local testfiles_base_path="./testsuite/testfiles"
  local tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')

  sed -i "s#JAVA_FLAGS#${JAVA_FLAGS}#;s#CLASSPATH#../target/classes#" testsuite/config/default.exp
  sed -i "s#TESTFILES_DIRECTORY#../${testfiles_base_path}#" testsuite/${tool}.tests/*.exp
}

secret_tests () {
  echo '⚙️ executing secret_tests'
  if [ ! -d ./testsuite ]; then
    echo "skipping secret tests because the testsuite folder does not exist."
  fi

  local step="secret"
  local tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
  cd testsuite || exit
  rm ${tool}.log || true
  timeout 60s runtest --tool ${tool} ${step}.exp || true
  cd ..
  pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log
  export secretExp="testsuite/${tool}.tests/secret.exp"
  if [ -f "${secretExp}" ]; then
    rm "${secretExp}"
  fi

  if [ -d "${testfiles_base_path}/secret" ]; then
    rm -rf "${testfiles_base_path}/secret"
  fi
}

public_tests () {
  echo '⚙️ executing public_tests'
  if [ ! -d ./testsuite ]; then
    echo "skipping public tests because the testsuite folder does not exist."
  fi

  local step="public"
  local tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
  cd testsuite || exit
  rm ${tool}.log || true
  timeout 60s runtest --tool ${tool} ${step}.exp || true
  cd ..
  pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log
}

advanced_tests () {
  echo '⚙️ executing advanced_tests'
  if [ ! -d ./testsuite ]; then
    echo "skipping advanced tests because the testsuite folder does not exist."
  fi

  local step="advanced"
  local tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
  cd testsuite || exit
  rm ${tool}.log || true
  timeout 60s runtest --tool ${tool} ${step}.exp || true
  cd ..
  pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log
}

static_code_analysis () {
  echo '⚙️ executing static_code_analysis'
  mvn -B checkstyle:checkstyle
  mkdir -p staticCodeAnalysisReports
  cp target/checkstyle-result.xml staticCodeAnalysisReports
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi
  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; build"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; main_method_checker"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; custom_checkers"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; replace_script_variables"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; secret_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; public_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; advanced_tests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; static_code_analysis"
}

main "${@}"
