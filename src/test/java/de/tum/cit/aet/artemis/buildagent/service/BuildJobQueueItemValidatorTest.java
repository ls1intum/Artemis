package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class BuildJobQueueItemValidatorTest {

    private static final String HOST = "https://artemis.example.org";

    private static String uri(String projectKey, String slug) {
        return HOST + "/git/" + projectKey + "/" + slug + ".git";
    }

    private static BuildJobQueueItem job(String assignmentUri, String testUri, String solutionUri, String[] auxiliaryUris, String[] auxiliaryDirectories,
            String assignmentCheckoutPath, String testCheckoutPath, String solutionCheckoutPath) {
        var repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, assignmentUri, testUri, solutionUri, auxiliaryUris, auxiliaryDirectories);
        var timing = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, assignmentCheckoutPath,
                testCheckoutPath, solutionCheckoutPath, null);
        return new BuildJobQueueItem("job-1", "name", new BuildAgentDTO("agent", "address", "display"), 1L, 2L, 3L, 0, 1, null, repositoryInfo, timing, buildConfig, null, null);
    }

    private static BuildJobQueueItem jobWithAssignmentUri(String assignmentUri) {
        return job(assignmentUri, uri("PROJ", "proj-tests"), null, new String[0], new String[0], null, null, null);
    }

    @Test
    void testUsualJobIsAccepted() {
        var job = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), uri("PROJ", "proj-solution"), new String[] { uri("PROJ", "proj-aux") }, new String[] { "aux" },
                "assignment", "tests/nested", "solution");

        assertThatCode(() -> BuildJobQueueItemValidator.validate(job)).doesNotThrowAnyException();
    }

    @Test
    void testUserNamesWithAllowedLoginCharactersAreAccepted() {
        var job = jobWithAssignmentUri(uri("PROJ", "proj-john.doe_1@tum"));

        assertThatCode(() -> BuildJobQueueItemValidator.validate(job)).doesNotThrowAnyException();
    }

    @Test
    void testHostOfTheUriIsNotRestricted() {
        // The agent rebuilds the clone address from its own configuration, so a core that reaches the version control
        // system under another name than the agent must keep working.
        var job = jobWithAssignmentUri("ssh://git@internal.example.org:7921/git/PROJ/proj-student1.git");

        assertThatCode(() -> BuildJobQueueItemValidator.validate(job)).doesNotThrowAnyException();
    }

    @Test
    void testCurrentDirectoryAndDefaultCheckoutPathsAreAccepted() {
        var job = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), null, new String[0], new String[0], ".", "", "  ");

        assertThatCode(() -> BuildJobQueueItemValidator.validate(job)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "..", "." })
    void testProjectKeyThatIsNotAPlainNameIsRejected(String projectKey) {
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(jobWithAssignmentUri(uri(projectKey, projectKey + "x")))).isInstanceOf(LocalCIException.class);
    }

    @Test
    void testSlugWithBackslashIsRejected() {
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(jobWithAssignmentUri(HOST + "/git/PROJ/proj-a%5C..%5Cb.git"))).isInstanceOf(LocalCIException.class);
    }

    @Test
    void testUriWithoutRepositoryPathIsRejected() {
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(jobWithAssignmentUri(HOST + "/somewhere/else"))).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(jobWithAssignmentUri("not a uri"))).isInstanceOf(LocalCIException.class);
    }

    @Test
    void testMissingRequiredUriIsRejected() {
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(jobWithAssignmentUri(null))).isInstanceOf(LocalCIException.class);
    }

    @Test
    void testOtherRepositoriesAreCheckedToo() {
        var badSolution = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), uri("..", "..x"), new String[0], new String[0], null, null, null);
        var badAuxiliary = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), null, new String[] { uri("..", "..x") }, new String[] { "aux" }, null, null, null);

        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(badSolution)).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(badAuxiliary)).isInstanceOf(LocalCIException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "..", "../outside", "a/../../b", "a\\..\\b", "a\u0000b" })
    void testCheckoutPathThatLeavesTheCheckoutDirectoryIsRejected(String path) {
        var assignment = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), null, new String[0], new String[0], path, null, null);
        var test = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), null, new String[0], new String[0], null, path, null);
        var solution = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), null, new String[0], new String[0], null, null, path);
        var auxiliary = job(uri("PROJ", "proj-student1"), uri("PROJ", "proj-tests"), null, new String[] { uri("PROJ", "proj-aux") }, new String[] { path }, null, null, null);

        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(assignment)).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(test)).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(solution)).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> BuildJobQueueItemValidator.validate(auxiliary)).isInstanceOf(LocalCIException.class);
    }
}
