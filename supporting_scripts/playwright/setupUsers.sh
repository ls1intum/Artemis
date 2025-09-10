#!/bin/bash

# We use the supporting scripts to create users

set -e
artemis_path="$(readlink -f "$(dirname "$0")/../..")"

cd "$artemis_path/supporting_scripts"

if [ ! -d "venv" ]; then
    python3 -m venv venv
fi

source venv/bin/activate

cd "$artemis_path/supporting_scripts/course-scripts/quick-course-setup"

python3 -m pip install -r requirements.txt
python3 create_users.py
