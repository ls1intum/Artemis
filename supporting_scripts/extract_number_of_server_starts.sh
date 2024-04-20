#!/bin/bash

numberOfStarts=$(grep ":: Powered by Spring Boot[^:]* ::" tests.log | wc -l)
echo "Number of Server Starts: $numberOfStarts"

if [[ $numberOfStarts -lt 1 ]]
then
  echo "Something went wrong, there should be at least one Server Start!"
  exit 1
fi

if [[ $numberOfStarts -ne 4 ]]
then
  echo "The number of Server Starts should be equal to 4! Please adapt this check if the change is intended or try to fix the underlying issue causing a different number of server starts!"
  exit 1
fi
