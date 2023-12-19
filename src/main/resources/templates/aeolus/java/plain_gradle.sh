#!/usr/bin/env bash
set -e
export AEOLUS_INITIAL_DIRECTORY=$(pwd)
# step gradle
# generated from step gradle
# original type was script
gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew
  ./gradlew clean test
}
# step setup_working_directory_for_cleanup
# generated from step setup_working_directory_for_cleanup
# original type was script
setupworkingdirectoryforcleanup () {
  echo '⚙️ executing setup_working_directory_for_cleanup'
  chmod -R 777 .
}

# move results
aeolus_move_results () {
  echo '⚙️ moving results'
  mkdir -p /var/tmp/aeolus-results
  shopt -s extglob
  cd "$AEOLUS_INITIAL_DIRECTORY"
  local _sources="**/test-results/test/*.xml"
  local _directory
  _directory=$(dirname "$_sources")
  mkdir -p /var/tmp/aeolus-results/"$_directory"
  mv $_sources /var/tmp/aeolus-results/**/test-results/test/*.xml
}

# always steps
final_aeolus_post_action () {
  set +e # from now on, we don't exit on errors
  echo '⚙️ executing final_aeolus_post_action'
  cd $AEOLUS_INITIAL_DIRECTORY
  setup_working_directory_for_cleanup "$_current_lifecycle"
  cd $AEOLUS_INITIAL_DIRECTORY
  aeolus_move_results "$_current_lifecycle"
  cd $AEOLUS_INITIAL_DIRECTORY
}


# main function
main () {
  local _current_lifecycle="${1}"
    if [[ "${_current_lifecycle}" == "aeolus_sourcing" ]]; then
    # just source to use the methods in the subshell, no execution
    return 0
  fi
  local _script_name
  _script_name=$(basename "$0")
  trap final_aeolus_post_action EXIT
  bash -c "source ${_script_name} aeolus_sourcing;gradle $_current_lifecycle"
  cd "$AEOLUS_INITIAL_DIRECTORY"
}

main $@
