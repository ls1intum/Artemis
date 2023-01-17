/*
 * This file configures the actual build steps for the automatic grading.
 *
 * !!!
 * For regular exercises, there is no need to make changes to this file.
 * Only this base configuration is actively supported by the Artemis maintainers
 * and/or your Artemis instance administrators.
 * !!!
 */

import java.time.ZoneId
import java.time.ZonedDateTime

dockerImage = "docker.io/ls1tum/artemis-maven-template:java17-9"
dockerFlags = ""

// based on a similar selection based on the `env.JOB_NAME` it would also
// be possible to create a setup with A/B testing where for each group a
// different set of tests is run;
// e.g., `hash(JOB_NAME) % N` to choose a different set of inputs for each
// group that remains consistent/stable for each student between submissions
isSolutionBuild = "${env.JOB_NAME}" ==~ /.+-SOLUTION$/
isTemplateBuild = "${env.JOB_NAME}" ==~ /.+-BASE$/

/**
 * Main function called by Jenkins.
 */
void testRunner() {
    setup()

    docker.image(dockerImage).inside(dockerFlags) { c ->
        runTestSteps()
    }

    // docker.image(dockerImage).inside(dockerFlags) { c ->
    //     runStuffInOtherContainer()
    // }

    // Jenkins security might prevent most method calls to the regular Java API
    // per default, they have to be approved by your Jenkins admin
    // boolean isAfterDueDate = ZonedDateTime.now().isAfter(ZonedDateTime.of(2030, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC+01:00")))
    // if (isSolutionBuild || isAfterDueDate) {
    //     docker.image(dockerImage).inside(dockerFlags) { c ->
    //         catchError {
    //             stage("Additional Tests") {
    //                 sh 'echo "Running some expensive additional tests"'
    //             }
    //         }
    //     }
    // }
}

private void runTestSteps() {
    catchError {
        test()
    }

    catchError {
        createCustomTestResult()
    }
}

/**
 * Runs special tasks before the actual tests can begin.
 * <p>
 * E.g. container image build, setting docker flags.
 */
private void setup() {
    if (isSolutionBuild) {
        // potential additional steps that should only be executed for the
        // solution repo
    } else {
        // if not solution repo, disallow network access from containers
        // dockerFlags += " --network none"
    }
}


/**
 * Run unit tests
 */
private void test() {
    stage('Test') {
        sh "./gradlew clean test"
    }
}

/**
 * See https://docs.artemis.ase.in.tum.de/user/exercises/programming/#jenkins
 */
private void createCustomTestResult() {
    stage('Custom') {
        sh """
        mkdir -p customFeedbacks
        echo '{ "name": "someUniqueId", "message": "for the student", "successful": true }' | tee customFeedbacks/dummy_test.json
        """
    }
}

/**
 * Runs the static code analysis
 */
private void staticCodeAnalysis() {
    stage("StaticCodeAnalysis") {
        sh """
        #staticCodeAnalysisScript
        """
    }
}

/**
 * Script of the post build tasks aggregating all JUnit files in $WORKSPACE/results.
 *
 * Called by Jenkins.
 */
void postBuildTasks() {
    if (#staticCodeAnalysisEnabled) {
        catchError {
            staticCodeAnalysis()
        }
    }
    sh 'rm -rf results'
    sh 'mkdir results'
    sh 'cp build/test-results/test/*.xml $WORKSPACE/results/ || true'
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
