#!/bin/bash

# Counts the Spring contexts a test run started, and refuses a run that starts too many: a context takes minutes to
# build, so an accidental extra one is a real cost on every build from then on.
#
# The bound is on the run as a whole. When the suite is sharded across jobs, each shard only starts the contexts of
# its own buckets and counts them from its own log; the reporting job sums those counts and passes the total here.
# Called without an argument, the count is taken from ./tests.log, which is what a single unsharded run leaves.
if [ -n "${1:-}" ]
then
  numberOfStarts="$1"
else
  # grep exits non-zero when it counts nothing or cannot read the file; either way that is zero starts, and the
  # bound below is what reports it.
  numberOfStarts=$(grep -c ":: Powered by Spring Boot[^:]* ::" tests.log || true)
fi
# Guard the comparisons below, which would otherwise read a non-numeric value as zero and blame the test run for it.
case "$numberOfStarts" in
  '' | *[!0-9]*)
    echo "Expected a number of server starts, got '$numberOfStarts'."
    exit 1
    ;;
esac
echo "Number of Server Starts: $numberOfStarts"

if [[ $numberOfStarts -lt 1 ]]
then
  echo "Something went wrong, there should be at least one Server Start!"
  exit 1
fi

# 11 = one per test bucket, plus the few classes that justify a context of their own. Raised from 10 when
# BucketLocalCILocalVC was split in two, which buys back more wall time on the critical path than the extra context
# costs. Raise it again only for a context that pays for itself the same way.
if [[ $numberOfStarts -gt 11 ]]
then
  echo "The number of Server Starts should be lower than/equals 11! Please adapt this check if the change is intended or try to fix the underlying issue causing a different number of server starts!"
  exit 1
fi
