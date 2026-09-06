package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

class LocalVCLocalCIParticipationIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "participationlocalvclocalci";

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Disabled // TODO enable - works isolated
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartParticipation() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        // Set the branch to null to force the usage of LocalVCService#getDefaultBranch().
        programmingExercise.getBuildConfig().setBranch(null);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase(Locale.ROOT) + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalVCTestRepository templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isPracticeMode()).isFalse();
        assertThat(participation.getStudent()).contains(user);
        LocalVCRepositoryUri studentAssignmentRepositoryUri = new LocalVCRepositoryUri(localVCBaseUri, projectKey,
                projectKey.toLowerCase(Locale.ROOT) + "-" + TEST_PREFIX + "student1");
        assertThat(studentAssignmentRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();

        var vcsAccessToken = request.get("/api/account/participation-vcs-access-token?participationId=" + participation.getId(), HttpStatus.OK, String.class);
        assertThat(vcsAccessToken).isNotNull();
        assertThat(vcsAccessToken).startsWith("vcpat");

        templateRepository.deleteWorkingCopy();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testStartParticipationRepairsUnparseableTemplateRepositoryUri() throws Exception {
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase(Locale.ROOT) + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        // Store the template repository URI in a legacy format (pre-LocalVC, no "git" path segment) that LocalVCRepositoryUri cannot parse.
        // Starting the exercise must repair the URI instead of failing with an internal server error (see issue #12840).
        templateParticipation.setRepositoryUri("https://bitbucket.example.com/scm/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalVCTestRepository templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();

        // The stored template repository URI should have been repaired to the canonical local VC format
        var repairedTemplateParticipation = templateProgrammingExerciseParticipationRepository.findById(templateParticipation.getId()).orElseThrow();
        assertThat(repairedTemplateParticipation.getRepositoryUri()).isEqualTo(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");

        templateRepository.deleteWorkingCopy();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testStartParticipationRepairsTemplateRepositoryUriPointingToMissingRepository() throws Exception {
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository (with the conventional slug) to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase(Locale.ROOT) + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        // Store a syntactically valid local VC URI that points to a repository which does not exist on disk.
        // Starting the exercise must fall back to the repository derived from the naming convention and repair the URI.
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase(Locale.ROOT) + "-doesnotexist.git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalVCTestRepository templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();

        // The stored template repository URI should have been repaired to point to the existing repository
        var repairedTemplateParticipation = templateProgrammingExerciseParticipationRepository.findById(templateParticipation.getId()).orElseThrow();
        assertThat(repairedTemplateParticipation.getRepositoryUri()).isEqualTo(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");

        templateRepository.deleteWorkingCopy();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testStartParticipationRepairsTemplateRepositoryUriPointingToAnotherProject() throws Exception {
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        // The stored URI points to a repository that really exists, but in a different project. The copy always reads from the project key of this exercise, so
        // accepting the stored URI would make the copy look for a repository that does not exist and skip the repair entirely (see issue #12840).
        String foreignProjectKey = projectKey + "OTHER";
        String foreignRepositorySlug = foreignProjectKey.toLowerCase(Locale.ROOT) + "-exercise";
        LocalVCTestRepository foreignRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(foreignProjectKey, foreignRepositorySlug);

        String templateRepositorySlug = projectKey.toLowerCase(Locale.ROOT) + "-exercise";
        LocalVCTestRepository templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);

        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + foreignProjectKey + "/" + foreignRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();

        // The URI must be repaired to the conventional repository of this exercise, not left pointing at the other project
        var repairedTemplateParticipation = templateProgrammingExerciseParticipationRepository.findById(templateParticipation.getId()).orElseThrow();
        assertThat(repairedTemplateParticipation.getRepositoryUri()).isEqualTo(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");

        templateRepository.deleteWorkingCopy();
        foreignRepository.deleteWorkingCopy();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLog() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "instructor1");
        var user = userTestRepository.getUser();
        vcsAccessLogRepository.save(new VcsAccessLog(user, participation, "instructor", "instructorMail@mail.de", RepositoryActionType.READ, AuthenticationMechanism.SSH, "", ""));
        var li = request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/vcs-access-log", HttpStatus.OK, VcsAccessLogDTO.class);
        assertThat(li.size()).isEqualTo(1);
        assertThat(li.getFirst().userId()).isEqualTo(user.getId());
    }

    /**
     * A build agent clone has no user behind it: the agent authenticates with the token of the build job it is running
     * rather than as a person, and the agent and job identify the access instead. This exercises the schema change that
     * made {@code vcs_access_log.user_id} nullable, which nothing else covers, and confirms the entry survives the
     * round trip through the DTO that used to dereference the user unconditionally.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLogOfBuildAgentWithoutUser() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        vcsAccessLogRepository.save(new VcsAccessLog(null, participation, "Build agent artemis-build-agent-1 (build job 42)", "", RepositoryActionType.PULL,
                AuthenticationMechanism.BUILD_JOB_TOKEN, "", "10.0.0.5"));

        var accessLogs = request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/vcs-access-log", HttpStatus.OK, VcsAccessLogDTO.class);

        assertThat(accessLogs).hasSize(1);
        assertThat(accessLogs.getFirst().userId()).as("a build agent clone is attributed to the agent and job, not to a user").isNull();
        assertThat(accessLogs.getFirst().name()).contains("artemis-build-agent-1").contains("42");
        assertThat(accessLogs.getFirst().authenticationMechanism()).isEqualTo(AuthenticationMechanism.BUILD_JOB_TOKEN.name());
    }

    /**
     * The amend-the-newest-entry lookups have to skip build agent rows.
     * <p>
     * A push writes its entry, queues the build, and only then fills in the commit hash on the newest entry of the
     * participation; a clone does the same for its clone-or-pull label. The agent's own clone of that repository lands
     * between the two, so without the exclusion the person's amendment would be written onto the agent's row - the
     * agent would appear to have pushed the commit, and the person's row would keep no hash at all.
     * <p>
     * Executing both queries here is also what validates them: an invalid {@code @Query} is only rejected when it is
     * first used, so one that no test calls would reach production intact.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNewestAccessLogLookupsSkipBuildAgentEntries() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var userEntry = vcsAccessLogRepository
                .save(new VcsAccessLog(user, participation, "instructor", "instructorMail@mail.de", RepositoryActionType.PUSH, AuthenticationMechanism.PASSWORD, "", "10.0.0.1"));
        // Saved after the person's entry, so it is the newest one and would win a lookup that did not exclude it
        vcsAccessLogRepository.save(new VcsAccessLog(null, participation, "Build agent artemis-build-agent-1 (build job 42)", "", RepositoryActionType.PULL,
                AuthenticationMechanism.BUILD_JOB_TOKEN, "", "10.0.0.5"));

        assertThat(vcsAccessLogRepository.findNewestUserEntryByParticipationId(participation.getId())).get().extracting(VcsAccessLog::getId).isEqualTo(userEntry.getId());
        assertThat(vcsAccessLogRepository.findNewestUserEntryByRepositoryUri(participation.getRepositoryUri())).get().extracting(VcsAccessLog::getId).isEqualTo(userEntry.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLogOfTemplateParticipation() throws Exception {
        var user = userTestRepository.getUser();
        vcsAccessLogRepository.save(new VcsAccessLog(user, programmingExercise.getTemplateParticipation(), "instructor", "instructorMail@mail.de", RepositoryActionType.READ,
                AuthenticationMechanism.SSH, "", ""));
        var li = request.getList("/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/TEMPLATE", HttpStatus.OK, VcsAccessLogDTO.class);
        assertThat(li.size()).isEqualTo(1);
        assertThat(li.getFirst().userId()).isEqualTo(user.getId());
    }

}
