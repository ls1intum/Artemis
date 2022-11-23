package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

class BuildLogEntryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Test
    void testGradleScenario() {
        var logs = convertToBuildLogs(
                """
                        Build ABC23H01E01 - AB12345 - Default Job #5 (MY-JOB) started building on agent ls1Agent-test.artemistest.in.tum.de, bamboo version: 8.2.5
                        Remote agent on host ls1Agent-test.artemistest.in.tum.de
                        Build working directory is /opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01
                        <div>Substituting variable: ${bamboo.working.directory} with /opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01</div>
                        <div>Substituting variable: ${bamboo.tmp.directory} with /opt/bambooagent/bamboo-agent-home/temp</div>
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker run --volume /opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker cp /tmp/initialiseDockerContainer.sh3940361644777320474.tmp c1d91d5a-630a-4479\s
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker exec -u root c1d91d5a-630a-4479-b176-146cb3bbbd88552479376 chown root:root /tm\s
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker exec -u root c1d91d5a-630a-4479-b176-146cb3bbbd88552479376 chmod 755 /tmp/init\s
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker exec c1d91d5a-630a-4479-b176-146cb3bbbd88552479376 /tmp/initialiseContainer.sh\s
                        Executing build ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)
                        Starting task 'Checkout Default Repository' of type 'com.atlassian.bamboo.plugins.vcs:task.vcs.checkout'
                        Checking out into /opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01-JOB1
                        Updating source code to revision: b3f71a4a21e72faf514bb1ae7f3803e7a542655d
                        Creating local git repository in '/opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01/.git'.
                        Initialized empty Git repository in /opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01/.git/
                        Fetching 'refs/heads/main' from 'ssh://git@bitbucket.ase.in.tum.de:7999/abc23h01e01/abc23h01e01-tests.git'.
                        Warning: Permanently added '[127.0.0.1]:46351' (RSA) to the list of known hosts.
                        From ssh://127.0.0.1:46351/abc23h0e01/abc23h0e01-tests
                        * [new branch]      main       -> main
                        Checking out revision b3f71a4a21e72faf514bb1ae7f3803e7a542655d.
                        Switched to branch 'main'
                        Updated source code to revision: b3f71a4a21e72faf514bb1ae7f3803e7a542655d
                        Checking out into /opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01-JOB1/assignment
                        Updating source code to revision: 315faad6e0e703c3f9bae2d13f06aeebde43c5aa
                        .git'.
                        .git/
                        Fetching 'refs/heads/main' from 'ssh://git@bitbucket.ase.in.tum.de:7999/abc23h0e01/abc23h01e01-user.git'.
                        From ssh://127.0.0.1:46351/abc23h0e01/abc23h0e01-user
                        Checking out revision 315faad6e0e703c3f9bae2d13f06aeebde43c5aa.
                        Updated source code to revision: 315faad6e0e703c3f9bae2d13f06aeebde43c5aa
                        Finished task 'Checkout Default Repository' with result: Success
                        Running pre-build action: Build Log Labeller Pre Build Action
                        Running pre-build action: VCS Version Collector
                        Starting task 'Tests' of type 'com.atlassian.bamboo.plugins.scripttask:task.builder.script'
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/tmp/runInDocker6842401286113283280.sh /opt/bambooagent/bamboo-agent-home/temp/ABC23H01E01
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
                        Failing task since return code of [/tmp/runInDocker6842401286113283280.sh /opt/bambooagent/bamboo-agent-home/temp/ABC23H01E01-JOB1-5-ScriptBuildTask-450090153682409851.sh] was 1 while expected 0
                        Finished task 'Tests' with result: Failed
                        Starting task 'JUnit Parser' of type 'com.atlassian.bamboo.plugins.testresultparser:task.testresultparser.junit'
                        Parsing test results under /opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01-JOB1...
                        Failing task since test cases were expected but none were found.
                        Finished task 'JUnit Parser' with result: Failed
                        Starting task 'Setup working directory for cleanup' of type 'com.atlassian.bamboo.plugins.scripttask:task.builder.script'
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/tmp/runInDocker2818428143557041492.sh /opt/bambooagent/bamboo-agent-home/temp/ABC23H01E01
                        Finished task 'Setup working directory for cleanup' with result: Success
                        Running post build plugin 'Docker Container Cleanup'
                        Running post build plugin 'NCover Results Collector'
                        Running post build plugin 'Build Results Label Collector'
                        Running post build plugin 'Clover Results Collector'
                        Running post build plugin 'npm Cache Cleanup'
                        Running post build plugin 'Artifact Copier'
                        Successfully removed working directory at '/opt/bambooagent/bamboo-agent-home/xml-data/build-dir/ABC23H01E01-JOB1'
                        Finalising the build...
                        Stopping timer.
                        Build ABC23H01E01-JOB1-5 completed.
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/tmp/runInDocker4293884529255884792.sh /opt/bambooagent/bamboo-agent-home/temp/cleanDirectory.
                        <div>Beginning to execute external process for build 'ABC23H01E01 - Default Job #5 (ABC23H01E01-JOB1-5)'<br /> ... running command line: <br />/usr/bin/docker rm -f c1d91d5a-630a-4479-b176-146cb3bbbd88552479376<br /> ... in: /opt/bambooa
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
                         """);

        var result = buildLogEntryService.removeUnnecessaryLogsForProgrammingLanguage(logs, ProgrammingLanguage.JAVA);
        assertThat(result).hasSizeLessThan(30);
    }

    private List<BuildLogEntry> convertToBuildLogs(List<String> content) {
        return convertToBuildLogs(content.stream());

    }

    private List<BuildLogEntry> convertToBuildLogs(String content) {
        return convertToBuildLogs(content.lines().map(String::strip));
    }

    private List<BuildLogEntry> convertToBuildLogs(Stream<String> content) {
        return content.map(text -> new BuildLogEntry(ZonedDateTime.now(), text)).collect(Collectors.toCollection(ArrayList::new));
    }

}
