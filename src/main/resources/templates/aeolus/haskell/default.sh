api: v0.0.1
#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step build_and_test_the_code
# generated from step build_and_test_the_code
# original type was script
build_and_test_the_code () {
  echo '⚙️ executing build_and_test_the_code'
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
  build_and_test_the_code $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
