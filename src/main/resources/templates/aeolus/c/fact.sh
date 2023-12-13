#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step setup_the_build_environment
# generated from step setup_the_build_environment
# original type was script
setup_the_build_environment () {
  echo '⚙️ executing setup_the_build_environment'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests
  # ------------------------------
  # Updating assignment and test-reports ownership...
  sudo chown artemis_user:artemis_user assignment/ -R || true
  sudo rm -rf test-reports
  sudo mkdir test-reports
  sudo chown artemis_user:artemis_user test-reports/ -R || true
}
# step build_and_run_all_tests
# generated from step build_and_run_all_tests
# original type was script
build_and_run_all_tests () {
  echo '⚙️ executing build_and_run_all_tests'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests
  # ------------------------------
  rm -f assignment/GNUmakefile
  rm -f assignment/Makefile
  cp -f tests/Makefile assignment/Makefile || exit 2
  cd tests
  python3 Tests.py
  rm Tests.py
  rm -rf ./tests || true
}
# step cleanup
# generated from step cleanup
# original type was script
cleanup () {
  echo '⚙️ executing cleanup'
  sudo rm -rf tests/ assignment/ test-reports/ || true
  chmod -R 777 .
}

# move results
aeolus_move_results () {
  echo '⚙️ moving results'
  mkdir -p /aeolus-results
  shopt -s extglob
  cd $AEOLUS_INITIAL_DIRECTORY
  local _sources="test-reports/tests-results.xml"
  mv $_sources /aeolus-results/test-reports/tests-results.xml
}

# always steps
final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd $AEOLUS_INITIAL_DIRECTORY
  cleanup $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  aeolus_move_results $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}


# main function
main () {
  local _current_lifecycle="${1}"
  trap final_aeolus_post_action EXIT
  setup_the_build_environment $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  build_and_run_all_tests $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
