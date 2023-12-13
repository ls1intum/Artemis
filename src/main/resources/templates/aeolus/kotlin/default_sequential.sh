#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step structural
# generated from step structural
# original type was script
structural () {
  echo '⚙️ executing structural'
  cd structural
  mvn clean test
}
# step behavior
# generated from step behavior
# original type was script
behavior () {
  echo '⚙️ executing behavior'
  cd behavior
  mvn clean test
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
  local _sources="**/target/surefire-reports/*.xml"
  mv $_sources /aeolus-results/**/target/surefire-reports/*.xml
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
  structural $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  behavior $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
