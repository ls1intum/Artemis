MAIN_CLASS = "notFound" // default value, will be replaced in Build stage
OUT_DIR = "target/surefire-reports"
javaFlags = ""
mavenFlags = "-B"

testfiles_base_path = "./testsuite/testfiles"

runDejagnuTests = fileExists("./testsuite")
tool = getToolName()
hasSecretTestFiles = secretTestFilesFolderExists()

dockerImage = '#dockerImage'
dockerFlags = '#dockerArgs'
isStaticCodeAnalysisEnabled = #isStaticCodeAnalysisEnabled

/**
 * Main function called by Jenkins.
 */
void testRunner() {
    setup()
    build()

    // catchError-block: execute following steps even if the one inside the
    // block fails, otherwise whole pipeline is aborted
    catchError {
        customCheckers()
    }
    catchError {
        if (runDejagnuTests) {
            dejagnuTests()
        }
    }
    catchError {
        staticCodeAnalysis()
    }
}

/**
 * Runs special tasks before the actual tests can begin.
 * <p>
 * E.g. container creation, setting docker flags.
 */
private void setup() {
    /* special jobs to run only for the solution repository
     * This may for example be useful when caching dependencies either in container volumes
     * or an external cache to enforce the appropriate access restrictions. You can find
     * more information on that in the documentation at
     * https://docs.artemis.tum.de/admin/production-setup/programming-exercise-adjustments#caching-docker-volumes
     * and
     * https://docs.artemis.tum.de/admin/production-setup/programming-exercise-adjustments#caching-sonatype-nexus
     */
    if ("${env.JOB_NAME}" ==~ /.+-SOLUTION$/) {
        // processing sample solution in this run
    } else {
        // processing student submission in this run
    }
}

/**
 * Builds the student code and tries to find a main method.
 */
private void build() {
    stage('Build') {
        docker.image(dockerImage).inside(dockerFlags) { c ->
            sh "mvn ${mavenFlags} clean compile"
            setMainClass()
        }
    }
}

/**
 * Runs the custom {@code pipeline-helper.jar} checkers.
 */
private void customCheckers() {
    stage('Checkers') {
        docker.image(dockerImage).inside(dockerFlags) { c ->
            // all java files in the assignment folder should have maximal line length 80
            sh 'pipeline-helper line-length -l 80 -s assignment/ -e java'
            // checks that the file exists and is not empty for non gui programs
            if (runDejagnuTests) {
                sh 'pipeline-helper file-exists assignment/Tests.txt'
            }
        }
    }
}

/**
 * Runs Dejagnu scripts.
 */
private void dejagnuTests() {
    stage('Secret Tests') {
        docker.image(dockerImage).inside(dockerFlags) { c ->
            applyExpectScriptReplacements()
            runDejagnuTestStep("secret", 60)
            removeSecretFiles()
        }
    }
    stage('Public+Advanced Tests') {
        docker.image(dockerImage).inside(dockerFlags) { c ->
            applyExpectScriptReplacements()

            runDejagnuTestStep("public", 60)
            runDejagnuTestStep("advanced", 60)
        }
    }
}

/**
 * Runs a single Dejagnu test step.
 *
 * @param step The test step name. Runs the test file {@code step.exp}.
 * @param timeoutSeconds A number of seconds after which Jenkins will kill the dejagnu process.
 */
private void runDejagnuTestStep(String step, int timeoutSeconds) {
    catchError() {
        timeout(time: timeoutSeconds, unit: 'SECONDS') {
            sh """
            cd testsuite || exit
            rm ${tool}.log || true
            runtest --tool ${tool} ${step}.exp || true
            """
        }
    }
    sh("""pipeline-helper -o customFeedbacks dejagnu -n "dejagnu[${step}]" -l testsuite/${tool}.log""")
}

/**
 * Extracts the Dejagnu tool name from the folder names.
 * <p>
 * E.g., for a folder {@code testsuite/gcd.tests/} the tool name is {@code gcd}.
 *
 * @return The Dejagnu tool name.
 */
private String getToolName() {
    // Java Files API gets blocked by Jenkins sandbox

    return sh(
        script: """find testsuite -name "*.tests" -type d -printf "%f" | sed 's#.tests\$##'""",
        returnStdout: true
    ).trim()
}

/**
 * Extracts the name of the student’s Java class that contains the main method
 * and stores it in {@code MAIN_CLASS}.
 * <p>
 * Aborts the pipeline if no main class could be found.
 */
private void setMainClass() {
    def main_checker_lines = sh(
        script: 'pipeline-helper main-method -s target/classes',
        returnStdout: true
    ).tokenize('\n')

    // first line of output is class name with main method
    // second one a status message that the checker ran successfully/failed
    if (main_checker_lines.size() == 2) {
        MAIN_CLASS = main_checker_lines.get(0)
    } else {
        // no main method found: let this stage fail, this aborts all further stages
        error
    }
}

/**
 * Replaces variables of the expect scripts with task specific values.
 */
private void applyExpectScriptReplacements() {
    sh """
    sed -i "s#JAVA_FLAGS#${javaFlags}#;s#CLASSPATH#../target/classes#" testsuite/config/default.exp
    sed -i "s#MAIN_CLASS#${MAIN_CLASS}#" testsuite/config/default.exp

    sed -i "s#TESTFILES_DIRECTORY#../${testfiles_base_path}#" testsuite/${tool}.tests/*.exp
    """
}

/**
 * Removes all secret files from the working directory.
 * <p>
 * Without the special Docker volume mounted then students can no longer access
 * those files during the public tests.
 */
private void removeSecretFiles() {
    secretExp = "testsuite/${tool}.tests/secret.exp"
    if (fileExists(secretExp)) {
        sh("rm ${secretExp}")
    }

    if (hasSecretTestFiles) {
        sh("rm -rf ${testfiles_base_path}/secret/")
    }
}

/**
 * Checks if a folder with secret Dejagnu test files exists.
 *
 * @return True, if such a folder exists.
 */
private boolean secretTestFilesFolderExists() {
    return fileExists("${testfiles_base_path}/secret")
}

/**
 * Runs the static code analysis
 */
private void staticCodeAnalysis() {
    if (!isStaticCodeAnalysisEnabled) {
        return
    }

    stage("StaticCodeAnalysis") {
        docker.image(dockerImage).inside(dockerFlags) { c ->
            /*
            sh """
            mvn -B spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd
            mkdir -p staticCodeAnalysisReports
            cp target/spotbugsXml.xml staticCodeAnalysisReports
            cp target/checkstyle-result.xml staticCodeAnalysisReports
            cp target/pmd.xml staticCodeAnalysisReports
            cp target/cpd.xml staticCodeAnalysisReports
            """
            */

            sh """
            mvn ${mavenFlags} checkstyle:checkstyle
            mkdir -p staticCodeAnalysisReports
            cp target/checkstyle-result.xml staticCodeAnalysisReports
            """
        }
    }
}

/**
 * Script of the post build tasks aggregating all JUnit files in $WORKSPACE/results.
 *
 * Called by Jenkins.
 */
void postBuildTasks() {
    // we do not actually have any JUnit-XMLs => no action needed
    /*
    sh '''
    rm -rf results
    mkdir results
    cp target/surefire-reports/*.xml $WORKSPACE/results/ || true
    sed -i 's/[^[:print:]\t]/�/g' $WORKSPACE/results/*.xml || true
    '''
    */
}

return this
