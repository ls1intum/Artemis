package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.programming.util.ZipTestUtil.extractExerciseJsonFromZip;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTheiaConfigDTO;
import de.tum.cit.aet.artemis.programming.icl.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class ProgrammingExerciseResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "programmingexerciseresource";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    protected UserTestRepository userTestRepository;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationTestRepo;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationTestRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCRepoPath;

    @BeforeEach
    void setup() {
        String studentParticipationGroupName = TEST_PREFIX + "studentParticipationGroup";

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Set<String> student1Groups = new HashSet<>(student1.getGroups());
        student1Groups.add(studentParticipationGroupName);
        student1.setGroups(student1Groups);
        userTestRepository.save(student1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course.setStudentGroupName(studentParticipationGroupName);
        course = courseRepository.save(course);

        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testBuildConfigOnlyReturnsRestrictedSetOfInformation() throws Exception {
        ProgrammingExerciseTheiaConfigDTO imageDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/theia-config", HttpStatus.OK,
                ProgrammingExerciseTheiaConfigDTO.class);

        // Count the number of fields in the record, this makes sure that only the expected fields are returned
        assertThat(imageDTO.getClass().getDeclaredFields().length).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportTemplateRepositoryAsInMemoryZip_shouldReturnValidZipWithContent() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = tempPath.resolve("testOriginRepo");
        localRepo.configureRepos(originRepoPath, "testLocalRepo", "testOriginRepo");

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        setupLocalVCRepository(localRepo, programmingExercise);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-repository/" + RepositoryType.TEMPLATE.name(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        // Verify that file is a valid ZIP file
        assertThat(result[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(result[1]).isEqualTo((byte) 0x4B); // 'K'

        ZipTestUtil.verifyZipStructureAndContent(result);

        // Clean up
        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportRepositoryWithFullHistory() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = tempPath.resolve("testOriginRepo");
        localRepo.configureRepos(originRepoPath, "testLocalRepo", "testOriginRepo");

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        setupLocalVCRepository(localRepo, programmingExercise);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-repository/" + RepositoryType.TEMPLATE.name(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        // Verify it's a valid ZIP file
        assertThat(result[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(result[1]).isEqualTo((byte) 0x4B); // 'K'

        // Verify that the zip contains the .git directory
        ZipTestUtil.verifyZipContainsGitDirectory(result);

        // Clean up
        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testExportStudentRequestedSolutionRepository_shouldReturnZipWithoutGit() throws Exception {
        programmingExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(2));
        programmingExerciseRepository.save(programmingExercise);

        String projectKey = programmingExercise.getProjectKey();
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";

        // Create LocalVC repo for solution first
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);

        programmingExercise = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);

        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-student-requested-repository?includeTests=false",
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        ZipTestUtil.verifyZipStructureAndContent(result);
        ZipTestUtil.verifyZipDoesNotContainGitDirectory(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testExportStudentRequestedTestsRepository_shouldReturnZipWithoutGit() throws Exception {
        // Example solution published and tests released with example solution
        programmingExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setReleaseTestsWithExampleSolution(true);
        programmingExerciseRepository.save(programmingExercise);

        // Prepare tests repository in LocalVC
        String projectKey = programmingExercise.getProjectKey();
        String testsRepositorySlug = projectKey.toLowerCase() + "-tests";
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, testsRepositorySlug);
        programmingExercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + testsRepositorySlug + ".git");
        programmingExerciseRepository.save(programmingExercise);

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-student-requested-repository?includeTests=true",
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
        // Verify zip is valid and does NOT contain .git
        ZipTestUtil.verifyZipStructureAndContent(result);
        ZipTestUtil.verifyZipDoesNotContainGitDirectory(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testExportOwnStudentRepository_shouldReturnZipWithoutGit() throws Exception {
        var participations = programmingExerciseStudentParticipationTestRepository.findByExerciseId(programmingExercise.getId());
        assertThat(participations).isNotEmpty();
        var studentParticipation = participations.getFirst();

        // Create a LocalVC repository for the student and wire the URI
        String projectKey = programmingExercise.getProjectKey();
        String repositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey, TEST_PREFIX + "student1");
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug);
        studentParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + repositorySlug + ".git");
        programmingExerciseStudentParticipationTestRepository.save(studentParticipation);

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-student-repository/" + studentParticipation.getId(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
        ZipTestUtil.verifyZipStructureAndContent(result);
        ZipTestUtil.verifyZipDoesNotContainGitDirectory(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportedExerciseJsonContainsDefaultCategories() throws Exception {
        // GIVEN
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        /*
         * The factory method populateUnreleasedProgrammingExercise() automatically
         * creates categories "cat1" and "cat2". We intentionally use these defaults.
         */
        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = tempPath.resolve("testOriginRepoCategories");
        localRepo.configureRepos(originRepoPath, "testLocalRepoCategories", "testOriginRepoCategories");
        setupLocalVCRepository(localRepo, programmingExercise);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // WHEN
        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-exercise", HttpStatus.OK, byte[].class);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        String exerciseJson = extractExerciseJsonFromZip(result);
        assertThat(exerciseJson).isNotBlank();

        var objectMapper = new ObjectMapper();
        var json = objectMapper.readTree(exerciseJson);

        assertThat(json.has("categories")).isTrue();
        var categoriesArray = json.get("categories");
        assertThat(categoriesArray.isArray()).isTrue();
        assertThat(categoriesArray).hasSize(2);

        // Verify the default factory categories
        List<String> categories = new ArrayList<>();
        categoriesArray.forEach(node -> categories.add(node.asText()));
        assertThat(categories).containsExactlyInAnyOrder("cat1", "cat2");

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportedExerciseJsonWithoutCategories() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        // Create a programming exercise and explicitly clear all categories
        // (The factory method populateUnreleasedProgrammingExercise() adds cat1 and cat2)
        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise.setCategories(new HashSet<>());
        programmingExerciseRepository.save(programmingExercise);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = tempPath.resolve("testOriginRepoNoCategories");
        localRepo.configureRepos(originRepoPath, "testLocalRepoNoCategories", "testOriginRepoNoCategories");
        setupLocalVCRepository(localRepo, programmingExercise);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-exercise", HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        String exerciseJson = extractExerciseJsonFromZip(result);
        assertThat(exerciseJson).isNotBlank();

        var objectMapper = new ObjectMapper();
        var json = objectMapper.readTree(exerciseJson);

        // Verify categories are not present
        assertThat(json.has("categories")).as("No categories field should be present").isFalse();

        localRepo.resetLocalRepo();
    }

    private void setupLocalVCRepository(LocalRepository localRepo, ProgrammingExercise exercise) throws Exception {
        String projectKey = exercise.getProjectKey();
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";

        // Create and configure the repository using LocalVCLocalCITestService
        LocalRepository localVCRepo = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);

        FileUtils.copyDirectory(localRepo.remoteBareGitRepo.getRepository().getDirectory(), localVCRepo.remoteBareGitRepoFile);

        // Set the proper LocalVC URI format
        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);
    }
}
