set -o pipefail

chmod +x ./changed-modules.sh
CHANGED_MODULES=$(./changed-modules.sh)

if [ -n "${CHANGED_MODULES}" ]; then
  # Always execute ArchitectureTests
  CHANGED_MODULES+=("ArchitectureTest")

  IFS=,
  TEST_MODULE_TAGS=$(echo "-DtestTags=${CHANGED_MODULES[*]}")
fi

echo "./gradlew --console=plain test jacocoTestReport -x webapp jacocoTestCoverageVerification $TEST_MODULE_TAGS | tee tests.log"
