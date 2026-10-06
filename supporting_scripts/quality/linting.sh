#!/bin/sh

cd "$(dirname "$0")/../.." || exit 1
./gradlew spotlessApply -PratchetFrom='develop'
