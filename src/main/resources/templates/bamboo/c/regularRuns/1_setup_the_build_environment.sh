#!/usr/bin/env bash

# ------------------------------
# Task Description:
# Build and run all tests
# ------------------------------

echo "--------------------setup-------------------"
echo "User:"
whoami
echo "Updating assignment and test-reports ownership..."
sudo chown artemis_user:artemis_user assignment/ -R
sudo rm -rf test-reports
sudo mkdir test-reports
sudo chown artemis_user:artemis_user test-reports/ -R
echo "--------------------setup-------------------"
echo "--------------------info--------------------"
python3 --version
pip3 --version
gcc --version
# Generic debug infos:
# tree
echo "--------------------info--------------------"
echo "--------------------tests-------------------"
ls -la tests
echo "--------------------tests-------------------"
echo "--------------------assignment--------------"
ls -la assignment
echo "--------------------assignment--------------"

cd tests
REQ_FILE=requirements.txt
if [ -f "$REQ_FILE" ]; then
    pip3 install --user -r requirements.txt
else
    echo "$REQ_FILE does not exist"
fi
exit 0