package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

class BuildLogEntryServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String GRADLE_SCENARIO = """
            Build ABC23H01E01 - AB12345 - Default Job #5 (MY-JOB) started building on agent ls1Agent-test.artemistest.in.tum.de, jenkins version: 8.2.5
            Remote agent on host ls1Agent-test.artemistest.in.tum.de
            Build working directory is /opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01
            Substituting variable: ${jenkins.working.directory} with /opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01
            Substituting variable: ${jenkins.tmp.directory} with /opt/jenkinsagent/jenkins-agent-home/temp
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker run --volume /opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker cp /tmp/initialiseDockerContainer.sh3940361644777320474.tmp c1d91d5a-630a-4479
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker exec -u root c1d91d5a-630a-4479-b176-146cb3bbbd88552479376 chown root:root /tm
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker exec -u root c1d91d5a-630a-4479-b176-146cb3bbbd88552479376 chmod 755 /tmp/init
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker exec c1d91d5a-630a-4479-b176-146cb3bbbd88552479376 /tmp/initialiseContainer.sh
            Executing build ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)
            Starting task 'Checkout Default Repository' of type 'com.atlassian.jenkins.plugins.vcs:task.vcs.checkout'
            Checking out into /opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01-JOB1
            Updating source code to revision: b3f71a4a21e72faf514bb1ae7f3803e7a542655d
            Creating local git repository in '/opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01/.git'.
            Initialized empty Git repository in /opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01/.git/
            Fetching 'refs/heads/main' from 'ssh://git@gitlab.ase.in.tum.de:7999/abc23h01e01/abc23h01e01-tests.git'.
            Warning: Permanently added '[127.0.0.1]:46351' (RSA) to the list of known hosts.
            From ssh://127.0.0.1:46351/abc23h0e01/abc23h0e01-tests
            * [new branch]      main       -> main
            Checking out revision b3f71a4a21e72faf514bb1ae7f3803e7a542655d.
            Switched to branch 'main'
            Updated source code to revision: b3f71a4a21e72faf514bb1ae7f3803e7a542655d
            Checking out into /opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01-JOB1/assignment
            Updating source code to revision: 315faad6e0e703c3f9bae2d13f06aeebde43c5aa
            .git'.
            .git/
            Fetching 'refs/heads/main' from 'ssh://git@gitlab.ase.in.tum.de:7999/abc23h0e01/abc23h01e01-user.git'.
            From ssh://127.0.0.1:46351/abc23h0e01/abc23h0e01-user
            Checking out revision 315faad6e0e703c3f9bae2d13f06aeebde43c5aa.
            Updated source code to revision: 315faad6e0e703c3f9bae2d13f06aeebde43c5aa
            Finished task 'Checkout Default Repository' with result: Success
            Running pre-build action: Build Log Labeller Pre Build Action
            Running pre-build action: VCS Version Collector
            Starting task 'Tests' of type 'com.atlassian.jenkins.plugins.scripttask:task.builder.script'
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/tmp/runInDocker6842401286113283280.sh /opt/jenkinsagent/jenkins-agent-home/temp/ABC23H01E01
            Starting a Gradle Daemon, 1 incompatible and 1 stopped Daemons could not be reused, use --status for details
            > Task :clean UP-TO-DATE
            > Task :compileJava FAILED
            src/de/tum/in/ase/Game.java:7: error: cannot find symbol
                 this.sizeX=sizeX;
                     ^
              symbol: variable sizeX
            src/de/tum/in/ase/Game.java:8: error: cannot find symbol
                 this.sizeY=sizeY;
              symbol: variable sizeY
            src/de/tum/in/ase/Game.java:12: error: cannot find symbol
                    return sizeX;
                            ^
              symbol:   variable sizeX
              location: class Game
            src/de/tum/in/ase/GameBoard.java:25: error: incompatible types: String cannot be converted to char
                   return "("+sizeX+","+sizeY+")";
                                 ^
            4 errors
            FAILURE: Build failed with an exception.
            * What went wrong:
            Execution failed for task ':compileJava'.
            > Compilation failed; see the compiler error output for details.
            2 actionable tasks: 1 executed, 1 up-to-date
            * Try:
            > Run with --stacktrace option to get the stack trace.
            > Run with --info or --debug option to get more log output.
            > Run with --scan to get full insights.
            * Get more help at https://help.gradle.org
            BUILD FAILED in 11s
            Failing task since return code of [/tmp/runInDocker6842401286113283280.sh /opt/jenkinsagent/jenkins-agent-home/temp/ABC23H01E01-JOB1-5-ScriptBuildTask-450090153682409851.sh] was 1 while expected 0
            Finished task 'Tests' with result: Failed
            Starting task 'JUnit Parser' of type 'com.atlassian.jenkins.plugins.testresultparser:task.testresultparser.junit'
            Parsing test results under /opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01-JOB1...
            Failing task since test cases were expected but none were found.
            Finished task 'JUnit Parser' with result: Failed
            Starting task 'Setup working directory for cleanup' of type 'com.atlassian.jenkins.plugins.scripttask:task.builder.script'
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/tmp/runInDocker2818428143557041492.sh /opt/jenkinsagent/jenkins-agent-home/temp/ABC23H01E01
            Finished task 'Setup working directory for cleanup' with result: Success
            Running post build plugin 'Docker Container Cleanup'
            Running post build plugin 'NCover Results Collector'
            Running post build plugin 'Build Results Label Collector'
            Running post build plugin 'Clover Results Collector'
            Running post build plugin 'npm Cache Cleanup'
            Running post build plugin 'Artifact Copier'
            Successfully removed working directory at '/opt/jenkinsagent/jenkins-agent-home/xml-data/build-dir/ABC23H01E01-JOB1'
            Finalising the build...
            Stopping timer.
            Build ABC23H01E01-JOB1-5 completed.
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/tmp/runInDocker4293884529255884792.sh /opt/jenkinsagent/jenkins-agent-home/temp/cleanDirectory.
            Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker rm -f c1d91d5a-630a-4479-b176-146cb3bbbd88552479376<br /> ... in: /opt/jenkinsa
            c1d91d5a-630a-4479-b176-146cb3bbbd88552479376
            Running on server: post build plugin 'Build Hanging Detection Configuration'
            Running on server: post build plugin 'NCover Results Collector'
            Running on server: post build plugin 'Build Labeller'
            Running on server: post build plugin 'Clover Delta Calculator'
            Running on server: post build plugin 'Maven Dependencies Postprocessor'
            All post build plugins have finished
            Generating build results summary...
            Saving build results to disk...
            Store variable context...
            Finished building ABC23H01E01-JOB1-5.
            """;

    private static final String MAVEN_SCENARIO = """
            Build mtc Test Maven - ARTEMISADMIN - Default Job #7 (MTCTSTMVN-ARTEMISADMIN-JOB1-7) started building on agent Agent1, jenkins version: 8.2.5
            the first of its kind
            Build working directory is /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1
            Unable to find image 'ls1tum/artemis-maven-template:java17-20' locally
            java17-20: Pulling from ls1tum/artemis-maven-template
            eaead16dc43b: Already exists
            b5503608cd3e: Already exists
            eae98b5113a2: Already exists
            e55eee3584d7: Already exists
            9061e3a33440: Already exists
            4e0db99c631f: Already exists
            c0a509b0e776: Already exists
            1bd9db93e080: Already exists
            a1a351a4ea8d: Pulling fs layer
            4f4fb700ef54: Pulling fs layer
            380917b8d95f: Pulling fs layer
            36b21db8cfd5: Pulling fs layer
            1d98a0f12066: Pulling fs layer
            73cbc73d72f9: Pulling fs layer
            36b21db8cfd5: Waiting
            1d98a0f12066: Waiting
            73cbc73d72f9: Waiting
            4f4fb700ef54: Download complete
            380917b8d95f: Verifying Checksum
            380917b8d95f: Download complete
            a1a351a4ea8d: Verifying Checksum
            a1a351a4ea8d: Download complete
            73cbc73d72f9: Verifying Checksum
            73cbc73d72f9: Download complete
            a1a351a4ea8d: Pull complete
            4f4fb700ef54: Pull complete
            380917b8d95f: Pull complete
            36b21db8cfd5: Verifying Checksum
            36b21db8cfd5: Download complete
            1d98a0f12066: Verifying Checksum
            1d98a0f12066: Download complete
            36b21db8cfd5: Pull complete
            1d98a0f12066: Pull complete
            73cbc73d72f9: Pull complete
            Digest: sha256:d82b8a02960018ed070279a3c850638fe6527e72af076cc90531163b9ed229e5
            Status: Downloaded newer image for ls1tum/artemis-maven-template:java17-20
            Executing build mtc Test Maven - ARTEMISADMIN - Default Job #7 (MTCTSTMVN-ARTEMISADMIN-JOB1-7)
            Starting task 'Checkout Default Repository' of type 'com.atlassian.jenkins.plugins.vcs:task.vcs.checkout'
            Checking out into /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1
            Updating source code to revision: 0d7998afec69f1a8d2432f060cb221c5e91af8bc
            Creating local git repository in '/var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/.git'.
            hint: Using 'master' as the name for the initial branch. This default branch name
            Initialized empty Git repository in /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/.git/
            hint: is subject to change. To configure the initial branch name to use in all
            hint: of your new repositories, which will suppress this warning, call:
            hint:
            hint: 	git config --global init.defaultBranch <name>
            hint:
            hint: Names commonly chosen instead of 'master' are 'main', 'trunk' and
            hint: 'development'. The just-created branch can be renamed via this command:
            hint:
            hint: 	git branch -m <name>
            Fetching 'refs/heads/main' from 'ssh://git@gitlab:7999/mtctstmvn/mtctstmvn-tests.git'.
            Warning: Permanently added '[127.0.0.1]:34027' (RSA) to the list of known hosts.
            From ssh://127.0.0.1:34027/mtctstmvn/mtctstmvn-tests
             * [new branch]      main       -> main
            Checking out revision 0d7998afec69f1a8d2432f060cb221c5e91af8bc.
            Switched to branch 'main'
            Updated source code to revision: 0d7998afec69f1a8d2432f060cb221c5e91af8bc
            Checking out into /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment
            Updating source code to revision: 924e7765ab252e65fc932e2a66a523748dd4e762
            Creating local git repository in '/var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/.git'.
            hint: Using 'master' as the name for the initial branch. This default branch name
            hint: is subject to change. To configure the initial branch name to use in all
            hint: of your new repositories, which will suppress this warning, call:
            hint:
            hint: 	git config --global init.defaultBranch <name>
            hint:
            hint: Names commonly chosen instead of 'master' are 'main', 'trunk' and
            hint: 'development'. The just-created branch can be renamed via this command:
            hint:
            hint: 	git branch -m <name>
            Initialized empty Git repository in /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/.git/
            Fetching 'refs/heads/main' from 'ssh://git@gitlab:7999/mtctstmvn/mtctstmvn-********.git'.
            Warning: Permanently added '[127.0.0.1]:34027' (RSA) to the list of known hosts.
            From ssh://127.0.0.1:34027/mtctstmvn/mtctstmvn-********
             * [new branch]      main       -> main
            Checking out revision 924e7765ab252e65fc932e2a66a523748dd4e762.
            Switched to branch 'main'
            Updated source code to revision: 924e7765ab252e65fc932e2a66a523748dd4e762
            Finished task 'Checkout Default Repository' with result: Success
            Running pre-build action: VCS Version Collector
            Running pre-build action: Build Log Labeller Pre Build Action
            Starting task 'Tests' of type 'com.atlassian.jenkins.plugins.maven:task.builder.mvn3'
            Beginning to execute external process for build 'mtc Test Maven - ARTEMISADMIN - Default Job #7 (MTCTSTMVN-ARTEMISADMIN-JOB1-7)'\\n ... running command line: \\n/artemis/bin/mvn --batch-mode -Djava.io.tmpdir=/opt/atlassian/jenkins/temp/MTCTSTMVN-ARTEMISADMIN-JOB1 clean test\\n ... in: /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1\\n
            NOTE: Picked up JDK_JAVA_OPTIONS:  --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED
            [INFO] Scanning for projects...
            [INFO]
            [INFO] --------------------< de.tum.tst:Test-Maven-Tests >---------------------
            [INFO] Building Test Maven Tests 1.0
            [INFO] --------------------------------[ jar ]---------------------------------
            [INFO]
            [INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ Test-Maven-Tests ---
            [INFO]
            [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ Test-Maven-Tests ---
            [INFO] Using 'UTF-8' encoding to copy filtered resources.
            [INFO] skip non existing resourceDirectory /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/src/main/resources
            [INFO]
            [INFO] --- maven-compiler-plugin:3.10.1:compile (default-compile) @ Test-Maven-Tests ---
            [INFO] Changes detected - recompiling the module!
            [INFO] Compiling 3 source files to /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/target/classes
            [INFO] -------------------------------------------------------------
            [ERROR] COMPILATION ERROR :
            [INFO] -------------------------------------------------------------
            [ERROR] /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/src/de/tum/tst/BubbleSort.java:[12,52] ';' expected
            [ERROR] /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/src/de/tum/tst/BubbleSort.java:[16,1] class, interface, enum, or record expected
            [INFO] 2 errors
            [INFO] -------------------------------------------------------------
            [INFO] ------------------------------------------------------------------------
            [INFO] BUILD FAILURE
            [INFO] ------------------------------------------------------------------------
            [INFO] Total time:  1.788 s
            [INFO] Finished at: 2022-11-23T08:58:45Z
            [INFO] ------------------------------------------------------------------------
            [ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.10.1:compile (default-compile) on project Test-Maven-Tests: Compilation failure: Compilation failure:
            [ERROR] /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/src/de/tum/tst/BubbleSort.java:[12,52] ';' expected
            [ERROR] /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/src/de/tum/tst/BubbleSort.java:[16,1] class, interface, enum, or record expected
            [ERROR] -> [Help 1]
            [ERROR]
            [ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
            [ERROR] Re-run Maven using the -X switch to enable full debug logging.
            [ERROR]
            [ERROR] For more information about the errors and possible solutions, please read the following articles:
            [ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
            Failing task since return code of [/artemis/bin/mvn --batch-mode -Djava.io.tmpdir=/opt/atlassian/jenkins/temp/MTCTSTMVN-ARTEMISADMIN-JOB1 clean test] was 1 while expected 0
            Parsing test results under /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1...
            Finished task 'Tests' with result: Failed
            Starting task 'Static Code Analysis' of type 'com.atlassian.jenkins.plugins.maven:task.builder.mvn3'
            Beginning to execute external process for build 'mtc Test Maven - ARTEMISADMIN - Default Job #7 (MTCTSTMVN-ARTEMISADMIN-JOB1-7)'\\n ... running command line: \\n/artemis/bin/mvn --batch-mode -Djava.io.tmpdir=/opt/atlassian/jenkins/temp/MTCTSTMVN-ARTEMISADMIN-JOB1 spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd\\n ... in: /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1\\n
            NOTE: Picked up JDK_JAVA_OPTIONS:  --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED
            [INFO] Scanning for projects...
            [INFO]
            [INFO] --------------------< de.tum.tst:Test-Maven-Tests >---------------------
            [INFO] Building Test Maven Tests 1.0
            [INFO] --------------------------------[ jar ]---------------------------------
            [INFO]
            [INFO] --- spotbugs-maven-plugin:4.7.2.0:spotbugs (default-cli) @ Test-Maven-Tests ---
            [INFO]
            [INFO] --- maven-checkstyle-plugin:3.2.0:checkstyle (default-cli) @ Test-Maven-Tests ---
            [INFO] Rendering content with org.apache.maven.skins:maven-default-skin:jar:1.3 skin.
            [INFO] ------------------------------------------------------------------------
            [INFO] BUILD FAILURE
            [INFO] ------------------------------------------------------------------------
            [INFO] Total time:  6.660 s
            [INFO] Finished at: 2022-11-23T08:58:56Z
            [INFO] ------------------------------------------------------------------------
            [ERROR] Failed to execute goal org.apache.maven.plugins:maven-checkstyle-plugin:3.2.0:checkstyle (default-cli) on project Test-Maven-Tests: An error has occurred in Checkstyle report generation. Failed during checkstyle configuration: Exception was thrown while processing /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/src/de/tum/tst/BubbleSort.java: IllegalStateException occurred while parsing file /var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1/assignment/src/de/tum/tst/BubbleSort.java. 15:4: no viable alternative at input '}': NoViableAltException -> [Help 1]
            [ERROR]
            [ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
            [ERROR] Re-run Maven using the -X switch to enable full debug logging.
            [ERROR]
            [ERROR] For more information about the errors and possible solutions, please read the following articles:
            [ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoExecutionException
            Failing task since return code of [/artemis/bin/mvn --batch-mode -Djava.io.tmpdir=/opt/atlassian/jenkins/temp/MTCTSTMVN-ARTEMISADMIN-JOB1 spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd] was 1 while expected 0
            Finished task 'Static Code Analysis' with result: Failed
            Running post build plugin 'Artifact Copier'
            Publishing an artifact: spotbugs
            Unable to publish artifact [spotbugs]:
            The artifact hasn't been successfully published after 3.326 ms
            Publishing an artifact: checkstyle
            Finished publishing of artifact Non required job artifact: [checkstyle], pattern: [checkstyle-result.xml] anchored at: [target] in 3.739 ms
            Publishing an artifact: pmd
            Unable to publish artifact [pmd]:
            The artifact hasn't been successfully published after 7.241 ms
            Publishing an artifact: pmd_cpd
            Unable to publish artifact [pmd_cpd]:
            The artifact hasn't been successfully published after 5.631 ms
            Running post build plugin 'npm Cache Cleanup'
            Running post build plugin 'NCover Results Collector'
            Running post build plugin 'Build Results Label Collector'
            Running post build plugin 'Clover Results Collector'
            Running post build plugin 'Docker Container Cleanup'
            Successfully removed working directory at '/var/atlassian/application-data/jenkins/local-working-dir/MTCTSTMVN-ARTEMISADMIN-JOB1'
            Finalising the build...
            Stopping timer.
            Build MTCTSTMVN-ARTEMISADMIN-JOB1-7 completed.
            Running on server: post build plugin 'Build Hanging Detection Configuration'
            Running on server: post build plugin 'NCover Results Collector'
            Running on server: post build plugin 'Build Labeller'
            Running on server: post build plugin 'Clover Delta Calculator'
            Running on server: post build plugin 'Maven Dependencies Postprocessor'
            All post build plugins have finished
            Generating build results summary...
            Saving build results to disk...
            Store variable context...
            Finished building MTCTSTMVN-ARTEMISADMIN-JOB1-7.
            """;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @ValueSource(strings = { GRADLE_SCENARIO, MAVEN_SCENARIO })
    @ParameterizedTest
    void testScenario(String scenario) {
        var logs = convertToBuildLogs(scenario);

        var result = buildLogEntryService.removeUnnecessaryLogsForProgrammingLanguage(logs, ProgrammingLanguage.JAVA);
        assertThat(result).hasSizeLessThan(30);
    }

    @Test
    void filterOutEmptyLogs() {
        var logs = convertToBuildLogs("", "   ", " ");
        var result = buildLogEntryService.removeUnnecessaryLogsForProgrammingLanguage(logs, ProgrammingLanguage.JAVA);
        assertThat(result).isEmpty();
    }

    private List<BuildLogEntry> convertToBuildLogs(List<String> content) {
        return convertToBuildLogs(content.stream());
    }

    private List<BuildLogEntry> convertToBuildLogs(String... content) {
        return convertToBuildLogs(Arrays.stream(content));
    }

    private List<BuildLogEntry> convertToBuildLogs(String content) {
        return convertToBuildLogs(content.lines().map(String::strip));
    }

    private List<BuildLogEntry> convertToBuildLogs(Stream<String> content) {
        return content.map(text -> new BuildLogEntry(ZonedDateTime.now(), text)).collect(Collectors.toCollection(ArrayList::new));
    }

}
