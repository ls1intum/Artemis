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

        # swift test
        swift test || true
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
    if [ -e assignment/tests.xml ]
    then
        sed -i 's/<testsuites>/<testsuite>/g ; s/<\\/testsuites>/<\\/testsuite>/g' assignment/tests.xml
        cp assignment/tests.xml $WORKSPACE/results/ || true
        sed -i 's/[^[:print:]\t]/�/g' $WORKSPACE/results/*.xml || true
    fi
    '''
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
