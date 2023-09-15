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

isSolutionBuild = "${env.JOB_NAME}" ==~ /.+-SOLUTION$/
isTestwiseCoverageEnabled = #testWiseCoverage && isSolutionBuild
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
    stage('Test') {
        if (isTestwiseCoverageEnabled) {
            sh 'mvn clean test -B -Pcoverage'
        } else {
            sh 'mvn clean test -B'
        }
    }
}

/**
 * Runs the static code analysis
 */
private void staticCodeAnalysis() {
    if (!isStaticCodeAnalysisEnabled) {
        return
    }

    stage("StaticCodeAnalysis") {
        sh '''
        rm -rf staticCodeAnalysisReports
        mkdir staticCodeAnalysisReports
        mvn spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd
        cp target/spotbugsXml.xml staticCodeAnalysisReports || true
        cp target/checkstyle-result.xml staticCodeAnalysisReports || true
        cp target/pmd.xml staticCodeAnalysisReports || true
        cp target/cpd.xml staticCodeAnalysisReports || true
        '''
    }
}

private void collectTestwiseCoverageReport() {
    catchError {
        sh '''
        rm -rf testwiseCoverageReport
        mkdir testwiseCoverageReport
        mv target/tia/reports/*/*.json testwiseCoverageReport/
        '''
    }
}

/**
 * Script of the post build tasks aggregating all JUnit files in $WORKSPACE/results.
 *
 * Called by Jenkins.
 */
void postBuildTasks() {
    if (isTestwiseCoverageEnabled) {
        collectTestwiseCoverageReport()
    }
    sh '''
    rm -rf results
    mkdir results
    cp target/surefire-reports/*.xml $WORKSPACE/results/ || true
    sed -i 's/[^[:print:]\t]/�/g' $WORKSPACE/results/*.xml || true
    '''
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
