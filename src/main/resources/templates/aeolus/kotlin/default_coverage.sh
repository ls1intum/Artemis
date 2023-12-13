#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step maven
# generated from step maven
# original type was script
maven () {
  echo '⚙️ executing maven'
  mvn clean test -Pcoverage
}
# step move_report_file
# generated from step move_report_file
# original type was script
move_report_file () {
  echo '⚙️ executing move_report_file'
  mv target/tia/reports/*/testwise-coverage-*.json target/tia/reports/tiaTests.json
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
  local _sources="target/tia/reports/tiaTests.json"
  mv $_sources /aeolus-results/target/tia/reports/tiaTests.json
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
  maven $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
  move_report_file $_current_lifecycle
  cd $AEOLUS_INITIAL_DIRECTORY
}

main $@
