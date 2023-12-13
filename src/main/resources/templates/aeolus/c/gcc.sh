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
  sudo chown artemis_user:artemis_user assignment/ -R
  sudo rm -rf test-reports
  mkdir test-reports
  chown artemis_user:artemis_user test-reports/ -R
  # assignment
  cd tests
  REQ_FILE=requirements.txt
  if [ -f "$REQ_FILE" ]; then
      pip3 install --user -r requirements.txt || true
  else
      echo "$REQ_FILE does not exist"
  fi
  cd ..
}
# step setup_makefile
# generated from step setup_makefile
# original type was script
setup_makefile () {
  echo '⚙️ executing setup_makefile'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Setup makefile
  # ------------------------------
  shadowFilePath="../tests/testUtils/c/shadow_exec.c"
  foundIncludeDirs=`grep -m 1 'INCLUDEDIRS\s*=' assignment/Makefile`
  foundSource=`grep -m 1 'SOURCE\s*=' assignment/Makefile`
  foundSource="$foundSource $shadowFilePath"
  rm -f assignment/GNUmakefile
  rm -f assignment/makefile
  cp -f tests/Makefile assignment/Makefile || exit 2
  sed -i "s~\bINCLUDEDIRS\s*=.*~${foundIncludeDirs}~; s~\bSOURCE\s*=.*~${foundSource}~" assignment/Makefile
}
# step build_and_run_all_tests
# generated from step build_and_run_all_tests
# original type was script
build_and_run_all_tests () {
  echo '⚙️ executing build_and_run_all_tests'
  #!/usr/bin/env bash
  # ------------------------------
  # Task Description:
  # Build and run all tests if the compilation succeeds
  # ------------------------------
  sudo chown artemis_user:artemis_user .
  gcc -c -Wall assignment/*.c || error=true
  if [ ! $error ]
  then
      cd tests || exit 0
      python3 Tests.py || true
  fi
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
  setup_makefile $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  build_and_run_all_tests $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
