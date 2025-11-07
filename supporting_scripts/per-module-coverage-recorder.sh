MODULES=("assessment" "athena" "atlas" "buildagent" "communication" "core" "exam" "exercise" "fileupload" "hyperion" "iris"
    "lecture" "lti" "modeling" "plagiarism" "programming" "quiz" "text" "tutorialgroup")

for module in "${MODULES[@]}"; do
    ./gradlew test jacocoTestReport -x webapp jacocoTestCoverageVerification -DincludeModules="$module" || true
done
