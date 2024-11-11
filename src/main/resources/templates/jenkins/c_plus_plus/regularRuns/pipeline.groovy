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
        mkdir test-reports

        # Updating ownership...
        chown -R artemis_user:artemis_user .

        REQ_FILE=requirements.txt
        if [ -f "$REQ_FILE" ]; then
            python3 -m venv /venv
            /venv/bin/pip3 install -r "$REQ_FILE"
        else
            echo "$REQ_FILE does not exist"
        fi
        '''
    }

    stage('Compile and Test') {
        sh '''
        if [ -d /venv ]; then
            . /venv/bin/activate
        fi

        # Run tests as unprivileged user
        runuser -u artemis_user python3 Tests.py
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
        sed -i 's/<testsuites[^>]*>/<testsuite>/g ; s/<\\/testsuites>/<\\/testsuite>/g' test-reports/tests-results.xml
    fi
    rm -rf results
    mv test-reports results
    '''
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
