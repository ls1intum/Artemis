#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step build_and_test_the_code
# generated from step build_and_test_the_code
# original type was script
build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
  # Delete possible old Sources and replace with student's assignment Sources
  rm -rf Sources
  mv assignment/Sources .
  # Delete and create the assignment directory from scratch
  mv assignment/.git/refs/heads assignment_git_heads # localci workaround
  rm -rf assignment
  mkdir assignment
  mkdir -p assignment/.git/refs # localci workaround
  mv assignment_git_heads/ assignment/.git/refs/heads/ # localci workaround
  cp -R Sources assignment
  # copy test files
  cp -R Tests assignment
  cp Package.swift assignment
  # In order to get the correct console output we need to execute the command within the assignment directory
  # swift build
  cd assignment
  swift build || error=true
  if [ ! $error ]
  then
      # swift test
      swift test || true
  fi
  # The used docker container is calling 'swift build' which creates files as root (e.g. tests.xml),
  # so we need to allow everyone to access these files
  cd ..
  chmod -R 777 .
}
# step run_static_code_analysis
# generated from step run_static_code_analysis
# original type was script
run_static_code_analysis () {
  echo '⚙️ executing run_static_code_analysis'
  # Copy SwiftLint rules
  cp .swiftlint.yml assignment || true
  # create target directory for SCA Parser
  mkdir target
  cd assignment
  # Execute static code analysis
  swiftlint > ../target/swiftlint-result.xml
}

# move results
aeolus_move_results () {
  echo '⚙️ moving results'
  mkdir -p /aeolus-results
  shopt -s extglob
  cd $AEOLUS_INITIAL_DIRECTORY
  local _sources="target/swiftlint-result.xml"
  mv $_sources /aeolus-results/target/swiftlint-result.xml
  local _sources="assignment/tests.xml"
  mv $_sources /aeolus-results/assignment/tests.xml
}

# always steps
final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd $AEOLUS_INITIAL_DIRECTORY
  run_static_code_analysis $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  aeolus_move_results $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}


# main function
main () {
  local _current_lifecycle="${1}"
  trap final_aeolus_post_action EXIT
  build_and_test_the_code $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
