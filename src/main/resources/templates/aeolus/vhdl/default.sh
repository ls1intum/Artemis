#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step provide_environment_information
# generated from step provide_environment_information
# original type was script
provide_environment_information () {
  echo '⚙️ executing provide_environment_information'
  #!/bin/bash
  echo "--------------------Python versions--------------------"
  python3 --version
  pip3 --version
  echo "--------------------Contents of tests repository--------------------"
  ls -la tests
  echo "---------------------------------------------"
  echo "--------------------Contents of assignment repository--------------------"
  ls -la assignment
  echo "---------------------------------------------"
  #Fallback in case Docker does not work as intended
  REQ_FILE=tests/requirements.txt
  if [ -f "$REQ_FILE" ]; then
      pip3 install --user -r tests/requirements.txt || true
  else
      echo "$REQ_FILE does not exist"
  fi
}
# step prepare_makefile
# generated from step prepare_makefile
# original type was script
prepare_makefile () {
  echo '⚙️ executing prepare_makefile'
  rm -f assignment/{GNUmakefile, Makefile, makefile}
  cp -f tests/Makefile assignment/Makefile || exit 2
}
# step run_and_compile
# generated from step run_and_compile
# original type was script
run_and_compile () {
  echo '⚙️ executing run_and_compile'
  cd tests
  python3 compileTest.py ../assignment/
  rm compileTest.py
  cp result.xml ../assignment/result.xml
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
  local _sources="assignment/result.xml"
  mv $_sources /aeolus-results/assignment/result.xml
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
  provide_environment_information $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  prepare_makefile $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  run_and_compile $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
