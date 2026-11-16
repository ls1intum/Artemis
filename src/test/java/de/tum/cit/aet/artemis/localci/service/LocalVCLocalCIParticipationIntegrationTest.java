package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationDTO;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.VcsAccessLogService;
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

    @Autowired
    private Optional<VcsAccessLogService> vcsAccessLogService;

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
        var buildConfig = programmingExerciseUtilService.buildConfigOf(programmingExercise);
        buildConfig.setBranch(null);
        programmingExerciseBuildConfigRepository.save(buildConfig);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase(Locale.ROOT) + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalVCTestRepository templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipationDTO participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipationDTO.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.testRun()).isFalse();
        assertThat(participation.student().getId()).isEqualTo(user.getId());
        LocalVCRepositoryUri studentAssignmentRepositoryUri = new LocalVCRepositoryUri(localVCBaseUri, projectKey,
                projectKey.toLowerCase(Locale.ROOT) + "-" + TEST_PREFIX + "student1");
        assertThat(studentAssignmentRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();

        var vcsAccessToken = request.get("/api/account/participation-vcs-access-token?participationId=" + participation.id(), HttpStatus.OK, String.class);
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
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase(Locale.ROOT) + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        // Store the template repository URI in a legacy format (pre-LocalVC, no "git" path segment) that LocalVCRepositoryUri cannot parse.
        // Starting the exercise must repair the URI instead of failing with an internal server error (see issue #12840).
        templateParticipation.setRepositoryUri("https://bitbucket.example.com/scm/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalVCTestRepository templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);

        StudentParticipationDTO participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipationDTO.class, HttpStatus.CREATED);
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
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository (with the conventional slug) to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase(Locale.ROOT) + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        // Store a syntactically valid local VC URI that points to a repository which does not exist on disk.
        // Starting the exercise must fall back to the repository derived from the naming convention and repair the URI.
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase(Locale.ROOT) + "-doesnotexist.git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalVCTestRepository templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);

        StudentParticipationDTO participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipationDTO.class, HttpStatus.CREATED);
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
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

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

        StudentParticipationDTO participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipationDTO.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();

        // The URI must be repaired to the conventional repository of this exercise, not left pointing at the other project
        var repairedTemplateParticipation = templateProgrammingExerciseParticipationRepository.findById(templateParticipation.getId()).orElseThrow();
        assertThat(repairedTemplateParticipation.getRepositoryUri()).isEqualTo(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");

        templateRepository.deleteWorkingCopy();
        foreignRepository.deleteWorkingCopy();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
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

    private static final List<RepositoryActionType> FETCH_TYPES = List.of(RepositoryActionType.PULL, RepositoryActionType.CLONE);

    private static final List<RepositoryActionType> PUSH_TYPES = List.of(RepositoryActionType.PUSH);

    private VcsAccessLog entry(User user, Participation participation, RepositoryActionType actionType) {
        return vcsAccessLogRepository.save(new VcsAccessLog(user, participation, "name", "mail@mail.de", actionType, AuthenticationMechanism.PASSWORD, "before", "10.0.0.1"));
    }

    private VcsAccessLog reload(VcsAccessLog accessLog) {
        return vcsAccessLogRepository.findById(accessLog.getId()).orElseThrow();
    }

    /**
     * The amend-an-entry lookup has to skip build agent rows.
     * <p>
     * A push writes its entry, queues the build, and only then fills in the commit hash; a clone does the same for its clone-or-pull label. The agent's own clone of that
     * repository lands between the two, so without the exclusion the person's amendment would be written onto the agent's row - the agent would appear to have pushed the
     * commit, and the person's row would keep no hash at all. The agent has no user, so a lookup by user never finds it.
     * <p>
     * Executing the query here is also what validates it: an invalid {@code @Query} is only rejected when it is first used, so one that no test calls would reach production
     * intact.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testNewestAccessLogLookupSkipsBuildAgentEntries() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var userEntry = entry(user, participation, RepositoryActionType.PULL);
        // Saved after the person's entry, so it is the newest one and would win a lookup that did not exclude it
        vcsAccessLogRepository.save(new VcsAccessLog(null, participation, "Build agent artemis-build-agent-1 (build job 42)", "", RepositoryActionType.PULL,
                AuthenticationMechanism.BUILD_JOB_TOKEN, "", "10.0.0.5"));

        var found = vcsAccessLogRepository.findNewestEntryOfUserForParticipation(user.getId(), participation.getId(), FETCH_TYPES, ZonedDateTime.now().minusHours(1));

        assertThat(found).get().extracting(VcsAccessLog::getId).isEqualTo(userEntry.getId());
    }

    /**
     * The failure this guards against: a clone arrives while the entry of its own handshake is not stored yet, so "the newest entry" is the push that came before. A lookup that
     * is not tied to the kind of operation then turns that push into a clone, and the log shows a clone where there was a push.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAFetchNeverTurnsAPushIntoAClone() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var push = entry(user, participation, RepositoryActionType.PUSH);

        vcsAccessLogService.orElseThrow().markNewestPullAsClone(user.getId(), participation.getId());

        assertThat(reload(push).getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAFetchTurnsOnlyItsOwnPullIntoAClone() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var earlierPull = entry(user, participation, RepositoryActionType.PULL);
        var clone = entry(user, participation, RepositoryActionType.PULL);
        var laterPush = entry(user, participation, RepositoryActionType.PUSH);

        vcsAccessLogService.orElseThrow().markNewestPullAsClone(user.getId(), participation.getId());

        // The newest entry of the fetch kind is the one of the running fetch, whatever was written after it
        assertThat(reload(clone).getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        assertThat(reload(earlierPull).getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(reload(laterPush).getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
    }

    /**
     * Two clones of one repository by one user that run at the same moment write two entries and then send two data transfer requests. Each request has to claim an entry of its
     * own, or one of the clones stays a pull.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testClonesAtTheSameMomentAreEachMarkedAsAClone() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var firstHandshake = entry(user, participation, RepositoryActionType.PULL);
        var secondHandshake = entry(user, participation, RepositoryActionType.PULL);

        vcsAccessLogService.orElseThrow().markNewestPullAsClone(user.getId(), participation.getId());
        vcsAccessLogService.orElseThrow().markNewestPullAsClone(user.getId(), participation.getId());

        assertThat(reload(firstHandshake).getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        assertThat(reload(secondHandshake).getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
    }

    @ParameterizedTest
    @EnumSource(value = AuthenticationMechanism.class, names = { "PASSWORD", "PARTICIPATION_VCS_ACCESS_TOKEN", "USER_VCS_ACCESS_TOKEN", "REPOSITORY_VCS_ACCESS_TOKEN" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testHttpsCloneClaimsItsHandshakeInsteadOfANewerSshPull(AuthenticationMechanism httpsMechanism) {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var service = vcsAccessLogService.orElseThrow();
        var httpsHandshake = service.createAccessLog(user, participation, RepositoryActionType.PULL, httpsMechanism, "https-head", "10.0.0.1");
        var sshPull = service.createAccessLog(user, participation, RepositoryActionType.PULL, AuthenticationMechanism.SSH, "ssh-head", "10.0.0.2");

        service.markNewestPullAsClone(user.getId(), participation.getId());

        assertThat(reload(httpsHandshake).getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        assertThat(reload(sshPull).getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(reload(sshPull).getCommitHash()).isEqualTo("ssh-head");
    }

    @ParameterizedTest
    @EnumSource(value = AuthenticationMechanism.class, names = { "PASSWORD", "PARTICIPATION_VCS_ACCESS_TOKEN", "USER_VCS_ACCESS_TOKEN", "REPOSITORY_VCS_ACCESS_TOKEN" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testHttpsPushAmendsItsHandshakeInsteadOfANewerSshPush(AuthenticationMechanism httpsMechanism) {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var service = vcsAccessLogService.orElseThrow();
        var httpsHandshake = service.createAccessLog(user, participation, RepositoryActionType.PUSH, httpsMechanism, "https-before", "10.0.0.1");
        var sshPush = service.createAccessLog(user, participation, RepositoryActionType.PUSH, AuthenticationMechanism.SSH, "ssh-before", "10.0.0.2");

        service.setCommitHashOfNewestPush(user.getId(), participation.getId(), "https-pushed");

        assertThat(reload(httpsHandshake).getCommitHash()).isEqualTo("https-pushed");
        assertThat(reload(sshPush).getCommitHash()).isEqualTo("ssh-before");
        assertThat(reload(sshPush).getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testHttpsAmendmentsLeaveSshEntriesAloneWhenTheirHandshakeIsMissing() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var service = vcsAccessLogService.orElseThrow();
        var sshPull = service.createAccessLog(user, participation, RepositoryActionType.PULL, AuthenticationMechanism.SSH, "ssh-pull", "10.0.0.2");
        var sshPush = service.createAccessLog(user, participation, RepositoryActionType.PUSH, AuthenticationMechanism.SSH, "ssh-push", "10.0.0.2");

        service.markNewestPullAsClone(user.getId(), participation.getId());
        service.setCommitHashOfNewestPush(user.getId(), participation.getId(), "https-pushed");

        assertThat(reload(sshPull).getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(reload(sshPush).getCommitHash()).isEqualTo("ssh-push");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAnEntryCanBeClaimedOnlyOnce() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var pull = entry(userTestRepository.getUser(), participation, RepositoryActionType.PULL);

        int first = vcsAccessLogRepository.changeActionTypeIfStill(pull.getId(), RepositoryActionType.PULL, RepositoryActionType.CLONE);
        int second = vcsAccessLogRepository.changeActionTypeIfStill(pull.getId(), RepositoryActionType.PULL, RepositoryActionType.CLONE);

        assertThat(first).isEqualTo(1);
        assertThat(second).as("the claim was lost, because the entry is no pull any more").isZero();
        assertThat(reload(pull).getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAPushNeverGivesItsCommitToAFetch() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var push = entry(user, participation, RepositoryActionType.PUSH);
        var laterFetch = entry(user, participation, RepositoryActionType.PULL);

        vcsAccessLogService.orElseThrow().setCommitHashOfNewestPush(user.getId(), participation.getId(), "pushed");

        assertThat(reload(push).getCommitHash()).isEqualTo("pushed");
        assertThat(reload(laterFetch).getCommitHash()).isEqualTo("before");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAmendmentsLeaveTheEntriesOfOtherUsersAndParticipationsAlone() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var otherParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        var user = userTestRepository.getUser();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var pullOfAStudent = entry(student, participation, RepositoryActionType.PULL);
        var pushOfAStudent = entry(student, participation, RepositoryActionType.PUSH);
        var pullInAnotherRepository = entry(user, otherParticipation, RepositoryActionType.PULL);
        var pushInAnotherRepository = entry(user, otherParticipation, RepositoryActionType.PUSH);

        // the instructor fetches and pushes in the repository of the first student, and has no entry there yet
        vcsAccessLogService.orElseThrow().markNewestPullAsClone(user.getId(), participation.getId());
        vcsAccessLogService.orElseThrow().setCommitHashOfNewestPush(user.getId(), participation.getId(), "pushed");

        assertThat(reload(pullOfAStudent).getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(reload(pushOfAStudent).getCommitHash()).isEqualTo("before");
        assertThat(reload(pullInAnotherRepository).getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(reload(pushInAnotherRepository).getCommitHash()).isEqualTo("before");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAnEntryOfAnEarlierOperationIsNotAmended() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();
        var oldPull = entry(user, participation, RepositoryActionType.PULL);
        ReflectionTestUtils.setField(oldPull, "timestamp", ZonedDateTime.now().minusHours(3));
        vcsAccessLogRepository.save(oldPull);

        // the entry of this fetch was never written, so the newest entry of the fetch kind belongs to an operation of hours ago
        vcsAccessLogService.orElseThrow().markNewestPullAsClone(user.getId(), participation.getId());

        assertThat(reload(oldPull).getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(vcsAccessLogRepository.findNewestEntryOfUserForParticipation(user.getId(), participation.getId(), PUSH_TYPES, ZonedDateTime.now().minusHours(1))).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testCreatingAnEntryStoresItBeforeReturning() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var user = userTestRepository.getUser();

        var created = vcsAccessLogService.orElseThrow().createAccessLog(user, participation, RepositoryActionType.PUSH, AuthenticationMechanism.SSH, "hash", "10.0.0.1");

        assertThat(created.getId()).isNotNull();
        assertThat(reload(created).getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetVcsAccessLogOfTemplateParticipation() throws Exception {
        var user = userTestRepository.getUser();
        vcsAccessLogRepository.save(new VcsAccessLog(user, programmingExercise.getTemplateParticipation(), "instructor", "instructorMail@mail.de", RepositoryActionType.READ,
                AuthenticationMechanism.SSH, "", ""));
        var li = request.getList("/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/TEMPLATE", HttpStatus.OK, VcsAccessLogDTO.class);
        assertThat(li.size()).isEqualTo(1);
        assertThat(li.getFirst().userId()).isEqualTo(user.getId());
    }

}
