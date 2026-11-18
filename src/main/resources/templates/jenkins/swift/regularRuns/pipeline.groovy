/*
 * This file configures the actual build steps for the automatic grading.
 *
 * !!!
 * For regular exercises, there is no need to make changes to this file.
 * Only this base configuration is actively supported by the Artemis maintainers
 * and/or your Artemis instance administrators.
 * !!!
 */

dockerImage = '#dockerImage'
dockerFlags = '#dockerArgs'

isStaticCodeAnalysisEnabled = #isStaticCodeAnalysisEnabled

/**
 * Main function called by Jenkins.
 */
void testRunner() {
    docker.image(dockerImage).inside(dockerFlags) { c ->
        runTestSteps()
    }
}

private void runTestSteps() {
    try {
        test()
    } finally {
        staticCodeAnalysis()
    }
}

/**
 * Run unit tests
 */
private void test() {
    stage('Build') {
        sh '''
        rm -rf Sources
        mv assignment/Sources .
        rm -rf assignment
        mkdir assignment
        cp -R Sources assignment
        cp -R Tests assignment
        cp Package.swift assignment

        # swift build
        cd assignment
        swift build

        # swift test: a failing test is part of the report and does not fail the stage, a run that ended abnormally does.
        # Swift Testing appends -swift-testing to the name of the xUnit file, so its results are in xunit-swift-testing.xml. XCTest with
        # SwiftTestReporter, which older exercises still use, writes tests.xml with the failure messages. The xUnit file of SwiftPM has another
        # name, because it would replace that report and lose the messages.
        # Both frameworks write the closing tag when the run ends, so a report without it comes from an interrupted run.
        status=0
        swift test --xunit-output xunit.xml || status=$?
        reports=0
        for report in tests.xml xunit-swift-testing.xml
        do
            if [ -e "$report" ]
            then
                if ! grep -q '</testsuites>' "$report"
                then
                    echo "The test report $report is incomplete, because the test run ended abnormally."
                    exit 1
                fi
                reports=$((reports + 1))
            fi
        done
        if [ "$status" -ne 0 ] && [ "$reports" -eq 0 ]
        then
            echo "The test run ended without a test report."
            exit "$status"
        fi
        '''
    }
}

private void staticCodeAnalysis() {
    if (!staticCodeAnalysisEnabled) {
        return
    }

    stage('Static Code Analysis') {
        sh '''
        rm -rf staticCodeAnalysisReports
        mkdir staticCodeAnalysisReports
        cp .swiftlint.yml assignment || true
        cd assignment
        swiftlint staticCodeAnalysisReports/swiftlint-result.xml
        '''
    }
}

/**
 * Script of the post build tasks aggregating all JUnit files in $WORKSPACE/results.
 *
 * Called by Jenkins.
 */
void postBuildTasks() {
    sh '''
    rm -rf results
    mkdir results
    for report in tests.xml xunit-swift-testing.xml
    do
        # an incomplete report comes from an interrupted run and is not passed on
        if [ -e "assignment/$report" ] && grep -q '</testsuites>' "assignment/$report"
        then
            cp "assignment/$report" $WORKSPACE/results/ || true
        fi
    done
    if ls $WORKSPACE/results/*.xml > /dev/null 2>&1
    then
        sed -i 's/[^[:print:]\t]/�/g' $WORKSPACE/results/*.xml || true
    fi
    '''
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
