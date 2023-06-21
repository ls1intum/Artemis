#!/bin/bash

numberOfStarts=$(grep ":: Interactive Learning with Individual Feedback ::" tests.log | wc -l)
echo "Number of Server Starts: $numberOfStarts"

if [[ $numberOfStarts -gt 4 ]]
then
  echo "The number of Server Starts should not be greater than 4!"
  exit 1
fi
