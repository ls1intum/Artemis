#!/usr/bin/env bash
set -e
build () {
  echo '⚙️ executing build'
  mvn -B clean compile
}

main_method_checker () {
  main_checker_output=$(pipeline-helper main-method -s target/classes)

  line_count="$(echo "$main_checker_output" | wc -l)"
  main_method="$(echo "$main_checker_output" | head -n1)"

  if [ "${line_count}" -eq 2 ]; then
      echo "main method found: ${main_method}"
    else
      echo "no main method found. quitting the test run."
      exit 1
    fi
}

custom_checkers () {
  echo '⚙️ executing custom checkers'
  # all java files in the assignment folder should have maximal line length 80
  pipeline-helper line-length -l 80 -s ${studentParentWorkingDirectoryName}/ -e java
  # checks that the file exists and is not empty for non gui programs
  pipeline-helper file-exists ${studentParentWorkingDirectoryName}/Tests.txt
}

secret_tests () {
  echo '⚙️ executing secret tests'

  export step="secret"
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
  echo '⚙️ executing public tests'

  export step="public"
  cd testsuite || exit
  rm ${tool}.log || true
  timeout 60s runtest --tool ${tool} ${step}.exp || true
  cd ..
  pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log
}

advanced_tests () {
  echo '⚙️ executing advanced tests'

  export step="advanced"
  cd testsuite || exit
  rm ${tool}.log || true
  timeout 60s runtest --tool ${tool} ${step}.exp || true
  cd ..
  pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log
}

replace_script_variables () {
    JAVA_FLAGS="-Djdk.console=java.base"

    sed -i "s#JAVA_FLAGS#${JAVA_FLAGS}#;s#CLASSPATH#../target/classes#" testsuite/config/default.exp
    sed -i "s#MAIN_CLASS#${MAIN_CLASS}#" testsuite/config/default.exp
    sed -i "s#TESTFILES_DIRECTORY#../${testfiles_base_path}#" testsuite/${tool}.tests/*.exp
}

main () {
  if [[ "${1}" == "aeolus_sourcing" ]]; then
    return 0 # just source to use the methods in the subshell, no execution
  fi

  local _script_name
  _script_name=${BASH_SOURCE[0]:-$0}

  bash -c "source ${_script_name} aeolus_sourcing; build"

  MAIN_CLASS=$(bash -c "source ${_script_name} aeolus_sourcing; main_method_checker")
  bash -c "source ${_script_name} aeolus_sourcing; custom_checkers"

  if [ -d ./testsuite ]; then
    export tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
    export testfiles_base_path="./testsuite/testfiles"

    replace_script_variables
    bash -c "source ${_script_name} aeolus_sourcing; secret_tests"
    bash -c "source ${_script_name} aeolus_sourcing; public_tests"
    bash -c "source ${_script_name} aeolus_sourcing; advanced_tests"
  else
    echo "skipping dejagnu tests because the testsuite folder does not exist."
  fi
}

main "${@}"
