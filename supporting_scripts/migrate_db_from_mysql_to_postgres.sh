#!/usr/bin/env bash

DIR=$(dirname "$(realpath "$0")")
PGLOADER_IMAGE="ghcr.io/dimitri/pgloader:latest"

confirm() {
    local question answer
    question="$1"
    if ! yes_no "$question"; then
        echo "Aborting"
        exit 1
    fi
}

yes_no() {
    local question answer
    question="$1"
    while true; do
        read -r -p "$question (Yes/No): " answer
        if [ "${answer,,}" == "yes" ] || [ "${answer,,}" == "y" ]; then
            return 0
        elif [ "${answer,,}" == "no" ] || [ "${answer,,}" == "n" ]; then
            return 1
        fi
        echo "Please enter either yes or no."
    done
}

get_value() {
    local type current first question answer
    type="$1"
    current="$2"
    first="true"

    while true; do
        if [ "$first" == "true" ]; then
            question="Current $type is: $current Press enter to accept it or enter a new one: "
        else
            question="Is $current correct? Confirm with enter or provide the correct $type: "
        fi
        read -r -p "$question" answer
        first="false"a
        if [ -z "$answer" ]; then
            break
        fi
        current="$answer"
    done
    echo -n "$current"
}

timestamp="$(date)"
echo "The migration output is logged to a file. The file will contain the"
echo "connection URIs as well as output from pgloader. That may include"
echo "sensitive information so please delete it after you are done."
echo "The log may be useful to troubleshoot a failed migration."
echo "The file is only appended. This log starts after the line containing $timestamp"
log_file=$(get_value "log file" "$(realpath mysql_to_postgresql_migration.log)")
echo "$timestamp" >> "$log_file"

echo "Checking docker"
if ! command -v docker &> /dev/null; then
    echo "This utility requires docker but docker is not installed."
    exit 1
fi
if ! docker info &> /dev/null; then
    echo "Docker daemon seems to be unavailable."
    exit 1
fi
if ! groups | grep -q 'docker'; then
    echo "Seems like you are not in the docker group."
    exit 1
fi

echo "Start database migration from MYSQL to PostgreSQL"
echo "First some checks"
confirm "Was the postgres database freshly initialised and all liquibase migrations where ran but no-one had accessed the server? (So there is no unexpected data present)"
confirm "Does SELECT * FROM artemis_version; return the same value in both databases?"
echo -e "\nNow you need to configure JDBC-URIs for MYSQL and PostgreSQL."
echo "Both need to have credentials for an account with full read (MYSQL) and full write (PostgreSQL)."
echo "The current values can be used to migrate the database created by the provided compose files"
echo "(./docker/mysql.yml to ./docker/postgres.yml)."

FROM_DB=$(get_value "from JDBC-URI" "mysql://root:@127.0.0.1:3306/artemis")
echo "from URI is $FROM_DB" >> "$log_file"
INTO_DB=$(get_value "into JDBC-URI" "pgsql://Artemis:@127.0.0.1:5432/Artemis")
echo "into URI is $INTO_DB" >> "$log_file"

echo
if yes_no "Should this be just a dry run? That will just check connectivity."; then
    opt="--dry-run"
    echo "This is just a dry run" >> "$log_file"
else
    opt=""
fi

echo
confirm "Sanity checks completed and configuration. Do you want to continue?"

echo "pgloader output:" >> "$log_file"
docker run --rm -it --net=host \
  -v "$DIR:/home/migration" \
  -e "FROM_DB=$FROM_DB" \
  -e "INTO_DB=$INTO_DB" \
  "$PGLOADER_IMAGE" \
  pgloader --dynamic-space-size 12000 --debug $opt /home/migration/migration.load | tee -a "$log_file"

case "$?" in
    125 |126|127)
        echo "Failed to run docker"
        exit 2
        ;;
    0);;
    *)
        echo "Migration failed"
        exit 1
        ;;
esac

echo
if yes_no "Should I clean up the docker image \"$PGLOADER_IMAGE\"?"; then
    docker image rm "$PGLOADER_IMAGE"
fi
