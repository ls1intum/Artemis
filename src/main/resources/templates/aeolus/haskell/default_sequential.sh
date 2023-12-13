#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step run_structural_tests
# generated from step run_structural_tests
# original type was script
run_structural_tests () {
  echo '⚙️ executing run_structural_tests'
  # the build process is specified in `run.sh` in the test repository
  # -s enables the safe testing mode
  chmod +x run.sh
  ./run.sh -s
}
# step run_behavior_tests
# generated from step run_behavior_tests
# original type was script
run_behavior_tests () {
  echo '⚙️ executing run_behavior_tests'
  # the build process is specified in `run.sh` in the test repository
  # -s enables the safe testing mode
  chmod +x run.sh
  ./run.sh -s
}
# step junit
# generated from step junit
# original type was script
junit () {
  echo '⚙️ executing junit'
  #empty script action, just for the results
}

# move results
aeolus_move_results () {
  echo '⚙️ moving results'
  mkdir -p /aeolus-results
  shopt -s extglob
  cd $AEOLUS_INITIAL_DIRECTORY
  local _sources="test-reports/results.xml"
  mv $_sources /aeolus-results/test-reports/results.xml
}

# always steps
final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd $AEOLUS_INITIAL_DIRECTORY
  junit $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  aeolus_move_results $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}


# main function
main () {
  local _current_lifecycle="${1}"
  trap final_aeolus_post_action EXIT
  run_structural_tests $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  run_behavior_tests $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
