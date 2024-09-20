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
    stage('Setup') {
        sh '''
        cd $WORKSPACE
        # Updating assignment and test-reports ownership...
        chown artemis_user:artemis_user assignment/ -R
        rm -rf test-reports
        mkdir test-reports
        chown artemis_user:artemis_user test-reports/ -R

        # assignment
        cd tests
        REQ_FILE=requirements.txt
        if [ -f "$REQ_FILE" ]; then
            pip3 install --user -r requirements.txt
        else
            echo "$REQ_FILE does not exist"
        fi
        exit 0
        '''
    }

    stage('Prepare Build') {
        sh '''
        #!/usr/bin/env bash

        shadowFilePath="../tests/testUtils/c/shadow_exec.c"

        foundIncludeDirs=`grep -m 1 'INCLUDEDIRS\\s*=' assignment/Makefile`

        foundSource=`grep -m 1 'SOURCE\\s*=' assignment/Makefile`
        foundSource="$foundSource $shadowFilePath"

        rm -f assignment/GNUmakefile
        rm -f assignment/makefile

        cp -f tests/Makefile assignment/Makefile || exit 2
        sed -i "s~\\bINCLUDEDIRS\\s*=.*~${foundIncludeDirs}~; s~\\bSOURCE\\s*=.*~${foundSource}~" assignment/Makefile
        '''
    }

    stage('Compile and Test') {
        sh  '''
        #!/bin/bash

        gcc -c -Wall assignment/*.c || error=true
        if [ ! $error ]
        then
            # Actual build process:
            cd tests
            python3 Tests.py s
            rm Tests.py
            rm -rf ./tests
            exit 0
        else
            # Compilation error
            exit 1
        fi
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
