#!/bin/bash

numberOfStarts=$(grep ":: Powered by Spring Boot[^:]* ::" tests.log | wc -l)
echo "Number of Server Starts: $numberOfStarts"

if [[ $numberOfStarts -lt 1 ]]
then
  echo "Something went wrong, there should be at least one Server Start!"
  exit 1
fi

if [[ $numberOfStarts -gt 5 ]]
then
  echo "The number of Server Starts should not be greater than 5!"
  exit 1
fi
