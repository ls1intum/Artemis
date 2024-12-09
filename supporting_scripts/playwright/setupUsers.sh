#!/bin/sh

# We use the supporting scripts to create users

set -e

cd ..

if [ ! -d "venv" ]; then
    python -m venv venv
fi

source venv/bin/activate

cd  course-scripts/quick-course-setup

python3 -m pip install -r requirements.txt
python3 create_users.py
