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
    pip3 install --user -r tests/requirements.txt
else
    echo "$REQ_FILE does not exist"
fi

exit 0
