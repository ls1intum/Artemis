package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

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
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ProgrammingExerciseResourceTest extends AbstractSpringIntegrationIndependentTest {

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
    void testFileAndDirectoryFilter() throws Exception {
        // Test the FileAndDirectoryFilter inner class functionality
        Class<?> filterClass = Class.forName("de.tum.cit.aet.artemis.programming.service.GitRepositoryExportService$FileAndDirectoryFilter");
        var constructor = filterClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object filter = constructor.newInstance();

        // Get both accept methods
        var acceptFileMethod = filterClass.getDeclaredMethod("accept", java.io.File.class);
        var acceptDirFileMethod = filterClass.getDeclaredMethod("accept", java.io.File.class, String.class);
        acceptFileMethod.setAccessible(true);
        acceptDirFileMethod.setAccessible(true);

        // Test that .git files/directories are filtered out
        var gitFile = new java.io.File(".git");
        assertThat((Boolean) acceptFileMethod.invoke(filter, gitFile)).isFalse();

        // Test that regular files are accepted
        var regularFile = new java.io.File("src/main/java/Test.java");
        assertThat((Boolean) acceptFileMethod.invoke(filter, regularFile)).isTrue();

        // Test the directory/filename variant
        var someDir = new java.io.File("src");
        assertThat((Boolean) acceptDirFileMethod.invoke(filter, someDir, "Test.java")).isTrue();

        // Test filtering .git directory with the two-parameter method
        var gitDir = new java.io.File(".git");
        assertThat((Boolean) acceptDirFileMethod.invoke(filter, gitDir, "anyfile.txt")).isFalse();
    }

    private void setupLocalVCRepository(LocalRepository localRepo, ProgrammingExercise exercise) throws Exception {
        String projectKey = exercise.getProjectKey();
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";

        // Create the repository folder in the LocalVC structure
        Path projectFolder = localVCRepoPath.resolve(projectKey);
        if (!Files.exists(projectFolder)) {
            Files.createDirectories(projectFolder);
        }
        Path repositoryFolder = projectFolder.resolve(templateRepositorySlug + ".git");
        Files.createDirectories(repositoryFolder);

        // Copy the bare repository to the LocalVC location
        org.apache.commons.io.FileUtils.copyDirectory(localRepo.remoteBareGitRepoFile, repositoryFolder.toFile());

        // Set the proper LocalVC URI format
        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);
    }
}
