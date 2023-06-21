#!/bin/bash

variable=$(grep "Interactive Learning with Individual Feedback" tests.log | wc -l)
echo $variable

if [[ $variable -gt 4 ]]
then
  echo "The number of Server Starts is greater than 4"
else
  echo "The number of Server Starts is not greater than 4"
fi
