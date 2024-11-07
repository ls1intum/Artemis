#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=${PWD}
build () {
  echo '⚙️ executing build'
  mvn -B clean compile
}

checkers () {
  echo '⚙️ executing checkers'
  # all java files in the assignment folder should have maximal line length 80
  pipeline-helper line-length -l 80 -s ${studentParentWorkingDirectoryName}/ -e java
  # checks that the file exists and is not empty for non gui programs
  pipeline-helper file-exists ${studentParentWorkingDirectoryName}/Tests.txt

  main_checker_output=$(pipeline-helper main-method -s target/classes)

  IFS=$'\n' read -rd '' -a main_checker_lines <<< "${main_checker_output}"

  if [ ${#main_checker_lines[@]} -eq 2 ]; then
    export MAIN_CLASS=${main_checker_lines[0]}
  else
    exit 1
  fi

  JAVA_FLAGS="-Djdk.console=java.base"

  sed -i "s#JAVA_FLAGS#${JAVA_FLAGS}#;s#CLASSPATH#../target/classes#" testsuite/config/default.exp
  sed -i "s#MAIN_CLASS#${MAIN_CLASS}#" testsuite/config/default.exp
  export testfiles_base_path="./testsuite/testfiles"
  export tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
  sed -i "s#TESTFILES_DIRECTORY#../${testfiles_base_path}#" testsuite/${tool}.tests/*.exp
}

secrettests () {
  echo '⚙️ executing secrettests'
  export tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
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

  export testfiles_base_path="./testsuite/testfiles"

  if [ -f "${testfiles_base_path}/secret" ]; then
    rm "${testfiles_base_path}/secret"
  fi
}

publictests () {
  echo '⚙️ executing publictests'
  export tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
  sed -i "s#TESTFILES_DIRECTORY#../${testfiles_base_path}#" testsuite/${tool}.tests/*.exp

  export tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
  export step="public"
  cd testsuite || exit
  rm ${tool}.log || true
  timeout 60s runtest --tool ${tool} ${step}.exp || true
  cd ..
  pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log
}

advancedtests () {
  echo '⚙️ executing advancedtests'
  export testfiles_base_path="./testsuite/testfiles"
  export tool=$(find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests$##')
  sed -i "s#TESTFILES_DIRECTORY#../${testfiles_base_path}#" testsuite/${tool}.tests/*.exp

  export step="advanced"
  cd testsuite || exit
  rm ${tool}.log || true
  timeout 60s runtest --tool ${tool} ${step}.exp || true
  cd ..
  pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log
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
  bash -c "source ${_script_name} aeolus_sourcing; checkers"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; secrettests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; publictests"
  cd "${AEOLUS_INITIAL_DIRECTORY}"
  bash -c "source ${_script_name} aeolus_sourcing; advancedtests"
}

main "${@}"
