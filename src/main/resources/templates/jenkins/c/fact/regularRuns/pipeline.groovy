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
    stage('Check Setup') {
        sh '''
        cd $WORKSPACE
        echo "--------------------setup-------------------"
        echo "User:"
        whoami
        echo "--------------------setup-------------------"
        echo "--------------------info--------------------"
        python3 --version
        pip3 --version
        pip3 freeze | grep fact
        gcc --version
        echo "--------------------tests-------------------"
        ls -la tests
        echo "--------------------tests-------------------"
        echo "--------------------assignment--------------"
        ls -la assignment
        echo "--------------------assignment--------------"

        exit 0
        '''
    }

    stage('Prepare Build') {
        sh '''
        rm -f assignment/GNUmakefile
        rm -f assignment/Makefile

        cp -f tests/Makefile assignment/Makefile || exit 2
        '''
    }

    stage('Compile and Test') {
        sh '''
        #!/bin/bash

        cd tests
        python3 Tests.py
        rm Tests.py
        rm -rf ./tests
        exit 0
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
    if [ -e test-reports/tests-results.xml ]
    then
        sed -i 's/[^[:print:]\t]/�/g' test-reports/tests-results.xml
        sed -i 's/<skipped/<error/g' test-reports/tests-results.xml
        sed -i 's/<\\/skipped>/<\\/error>/g' test-reports/tests-results.xml
    fi
    rm -rf results
    mv test-reports results
    '''
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
