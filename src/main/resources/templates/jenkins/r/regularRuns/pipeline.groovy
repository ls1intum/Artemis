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

/**
 * Main function called by Jenkins.
 */
void testRunner() {
    docker.image(dockerImage).inside(dockerFlags) { c ->
        runTestSteps()
    }
}

private void runTestSteps() {
    test()
}

/**
 * Run unit tests
 */
private void test() {
    stage('Test') {
        sh '''
        Rscript -e 'remotes::install_local()'
        Rscript -e 'library("testthat"); options(testthat.output_file = "junit.xml"); test_local(".", reporter = "junit")'
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
    cp tests/testthat/junit.xml $WORKSPACE/results/ || true
    sed -i 's/[^[:print:]\t]/�/g' $WORKSPACE/results/*.xml || true
    '''
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
