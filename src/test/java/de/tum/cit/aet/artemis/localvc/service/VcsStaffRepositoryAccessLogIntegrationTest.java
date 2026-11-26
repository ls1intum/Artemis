package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsStaffRepositoryAccessLog;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsStaffRepositoryAccessLogRepository;
import de.tum.cit.aet.artemis.programming.service.VcsAccessLogOperations;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * The access log of the tests and auxiliary repositories. They belong to the exercise and have no participation, so their entries refer to the exercise and to the repository
 * and are kept in a table of their own, which keeps them out of the log of the solution repository.
 */
class VcsStaffRepositoryAccessLogIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "vcsstaffaccess";

    private static final String AUXILIARY_NAME = "extras";

    @Autowired
    private VcsStaffRepositoryAccessLogRepository staffRepositoryAccessLogRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private VcsAccessLogService vcsAccessLogService;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    private LocalVCTestRepository testsRepository;

    private final List<Path> clones = new ArrayList<>();

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException, InvalidNameException {
        testsRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug);
        for (String login : List.of(instructor1Login, student1Login)) {
            var user = new LdapUserDto().login(login);
            user.setUid(new LdapName("cn=" + login + ",ou=test,o=lab"));
            doReturn(Optional.of(user)).when(ldapUserService).findByLogin(login);
        }
        doReturn(true).when(ldapTemplate).compare(anyString(), anyString(), any());
        dockerClientTestService.mockInspectImage(dockerClient);
        vcsAccessLogRepository.deleteAll();
        staffRepositoryAccessLogRepository.deleteAll();
    }

    @AfterEach
    void removeRepositories() throws IOException {
        testsRepository.deleteWorkingCopy();
        for (Path clone : clones) {
            FileUtils.deleteDirectory(clone.toFile());
        }
        clones.clear();
        staffRepositoryAccessLogRepository.deleteAll();
    }

    private Git clone(String login, String slug) throws GitAPIException, IOException {
        Path directory = tempFileUtilService.createTempDirectory("vcs-staff-access-log-clone");
        clones.add(directory);
        return Git.cloneRepository().setURI(localVCLocalCITestService.buildLocalVCUri(login, projectKey1, slug)).setDirectory(directory.toFile())
                .setCredentialsProvider(LocalVCLocalCITestService.ONLY_THE_CREDENTIALS_IN_THE_URI).call();
    }

    private AuxiliaryRepository createAuxiliaryRepository() throws GitAPIException, IOException {
        var auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName(AUXILIARY_NAME);
        auxiliaryRepository.setCheckoutDirectory(AUXILIARY_NAME);
        auxiliaryRepository.setExercise(programmingExercise);
        auxiliaryRepository = auxiliaryRepositoryRepository.save(auxiliaryRepository);
        localVCLocalCITestService.createRepository(projectKey1, projectKey1.toLowerCase(Locale.ROOT) + "-" + AUXILIARY_NAME);
        return auxiliaryRepository;
    }

    /** What the log shows for the tests repository: one entry per operation. */
    private List<VcsStaffRepositoryAccessLog> testsEntries() {
        return VcsAccessLogOperations
                .oneEntryPerOperation(staffRepositoryAccessLogRepository.findAllByExerciseIdAndRepositoryType(programmingExercise.getId(), RepositoryType.TESTS));
    }

    private List<VcsStaffRepositoryAccessLog> awaitTestsEntries(int count) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> testsEntries().size() >= count && testsEntries().stream().noneMatch(VcsStaffRepositoryAccessLogIntegrationTest::isAdvertisement));
        return testsEntries();
    }

    private static boolean isAdvertisement(VcsStaffRepositoryAccessLog entry) {
        return entry.getRepositoryActionType() == RepositoryActionType.READ || entry.getRepositoryActionType() == RepositoryActionType.WRITE;
    }

    /** The build agents that clone the repositories for the build of a push write entries of their own, which have no user, so the entries of people are looked at. */
    private void assertTheSolutionLogIsEmpty() {
        assertThat(vcsAccessLogRepository.findAllByParticipationId(solutionParticipation.getId())).filteredOn(entry -> entry.getUser() != null).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aCloneOfTheTestsRepositoryIsLoggedForTheExerciseAndNotForTheSolution() throws Exception {
        String head = testsRepository.workingCopy().getRepository().resolve("HEAD").name();

        try (Git _ = clone(instructor1Login, testsRepositorySlug)) {
            assertThat(awaitTestsEntries(1)).singleElement().satisfies(entry -> {
                assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
                assertThat(entry.getRepositoryType()).isEqualTo(RepositoryType.TESTS);
                assertThat(entry.getAuxiliaryRepository()).isNull();
                assertThat(entry.getUser().getLogin()).isEqualTo(instructor1Login);
                assertThat(entry.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.PASSWORD);
                assertThat(entry.getCommitHash()).isEqualTo(head);
                assertThat(entry.getExercise().getId()).isEqualTo(programmingExercise.getId());
            });
        }
        assertTheSolutionLogIsEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aPushToTheTestsRepositoryIsLoggedWithTheCommitThatWasPushed() throws Exception {
        String commit = localVCLocalCITestService.commitFile(testsRepository.workingCopyPath(), testsRepository.workingCopy(), "tests-file.txt");
        localVCLocalCITestService.testPushSuccessful(testsRepository.workingCopy(), instructor1Login, projectKey1, testsRepositorySlug);

        assertThat(awaitTestsEntries(1)).singleElement().satisfies(entry -> {
            assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
            assertThat(entry.getUser().getLogin()).isEqualTo(instructor1Login);
            assertThat(entry.getCommitHash()).isEqualTo(commit);
        });
        assertTheSolutionLogIsEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aRefusedAttemptOnTheTestsRepositoryIsLoggedForTheExerciseAndNotForTheSolution() {
        assertThatThrownBy(() -> clone(student1Login, testsRepositorySlug).close()).isInstanceOf(GitAPIException.class);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(testsEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE_FAIL);
            assertThat(entry.getUser().getLogin()).isEqualTo(student1Login);
        }));
        assertTheSolutionLogIsEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aCloneOfAnAuxiliaryRepositoryIsLoggedWithTheRepository() throws Exception {
        var auxiliaryRepository = createAuxiliaryRepository();

        try (Git _ = clone(instructor1Login, projectKey1.toLowerCase(Locale.ROOT) + "-" + AUXILIARY_NAME)) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(auxiliaryEntries(auxiliaryRepository)).singleElement().satisfies(entry -> {
                assertThat(entry.getRepositoryType()).isEqualTo(RepositoryType.AUXILIARY);
                assertThat(entry.getRepositoryName()).isEqualTo(AUXILIARY_NAME);
                assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
                assertThat(entry.getUser().getLogin()).isEqualTo(instructor1Login);
            }));
        }
        assertThat(testsEntries()).isEmpty();
        assertTheSolutionLogIsEmpty();
    }

    private List<VcsStaffRepositoryAccessLog> auxiliaryEntries(AuxiliaryRepository auxiliaryRepository) {
        return VcsAccessLogOperations.oneEntryPerOperation(staffRepositoryAccessLogRepository.findAllByAuxiliaryRepositoryId(auxiliaryRepository.getId()));
    }

    private VcsStaffRepositoryAccessLog store(String login, RepositoryType type, AuxiliaryRepository auxiliaryRepository, RepositoryActionType action, ZonedDateTime timestamp) {
        var target = new AccessLogTarget.OfStaffRepository(programmingExercise.getId(), type, auxiliaryRepository == null ? null : auxiliaryRepository.getId(),
                auxiliaryRepository == null ? null : auxiliaryRepository.getName());
        return vcsAccessLogService.createStaffRepositoryAccessLog(userUtilService.getUserByLogin(login), target, action, AuthenticationMechanism.PASSWORD, "hash", "10.0.0.1",
                timestamp);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void theInstructorReadsTheLogOfTheTestsRepository() throws Exception {
        store(instructor1Login, RepositoryType.TESTS, null, RepositoryActionType.PUSH, ZonedDateTime.now());

        var log = request.getList("/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/TESTS", HttpStatus.OK, VcsAccessLogDTO.class);

        assertThat(log).singleElement().satisfies(entry -> {
            assertThat(entry.repositoryActionType()).isEqualTo("PUSH");
            assertThat(entry.authenticationMechanism()).isEqualTo("PASSWORD");
            assertThat(entry.userId()).isEqualTo(instructor1.getId());
            assertThat(entry.buildAgent()).isFalse();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void theLogOfTheTestsRepositoryHoldsNothingOfTheAuxiliaryRepositories() throws Exception {
        var auxiliaryRepository = createAuxiliaryRepository();
        store(instructor1Login, RepositoryType.AUXILIARY, auxiliaryRepository, RepositoryActionType.CLONE, ZonedDateTime.now());

        var testsLog = request.getList("/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/TESTS", HttpStatus.OK, VcsAccessLogDTO.class);
        var auxiliaryLog = request.getList(
                "/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/AUXILIARY?repositoryId=" + auxiliaryRepository.getId(), HttpStatus.OK,
                VcsAccessLogDTO.class);

        assertThat(testsLog).isEmpty();
        assertThat(auxiliaryLog).singleElement().satisfies(entry -> assertThat(entry.repositoryActionType()).isEqualTo("CLONE"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void anAuxiliaryRepositoryHasToBeNamedAndHasToExist() throws Exception {
        var auxiliaryRepository = createAuxiliaryRepository();
        String path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/AUXILIARY";

        request.get(path, HttpStatus.BAD_REQUEST, List.class);
        request.get(path + "?repositoryId=" + (auxiliaryRepository.getId() + 1000), HttpStatus.NOT_FOUND, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void aTutorCannotReadTheLog() throws Exception {
        request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/TESTS", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void onlyTheRepositoriesOfAnExerciseHaveALog() throws Exception {
        request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/vcs-access-log/USER", HttpStatus.BAD_REQUEST, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void theEntriesOfARemovedAuxiliaryRepositoryStayWithItsName() throws Exception {
        var auxiliaryRepository = createAuxiliaryRepository();
        var entry = store(instructor1Login, RepositoryType.AUXILIARY, auxiliaryRepository, RepositoryActionType.CLONE, ZonedDateTime.now());

        auxiliaryRepositoryRepository.deleteById(auxiliaryRepository.getId());

        var stored = staffRepositoryAccessLogRepository.findById(entry.getId()).orElseThrow();
        assertThat(stored.getAuxiliaryRepository()).isNull();
        assertThat(stored.getRepositoryName()).isEqualTo(AUXILIARY_NAME);
    }
}
