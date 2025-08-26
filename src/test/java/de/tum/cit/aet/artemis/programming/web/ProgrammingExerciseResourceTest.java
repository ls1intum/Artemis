package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
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
import de.tum.cit.aet.artemis.programming.service.GitRepositoryExportService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepositoryUriUtil;
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

    // This will be a spy bean since it is configured as @MockitoSpyBean in the parent class
    @Autowired
    private GitRepositoryExportService gitRepositoryExportService;

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

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

        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation
                .setRepositoryUri(new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, originRepoPath)).getURI().toString());
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // Mock the export methods to return valid resources
        var files = java.util.Map.of("test.txt", "test content");
        byte[] mockZipData = ZipTestUtil.createTestZipFile(files);
        InputStreamResource mockZipResource = ZipTestUtil.createMockZipResource(mockZipData, "mock-repo.zip");
        doReturn(mockZipResource).when(gitRepositoryExportService).exportInstructorRepositoryForExerciseInMemory(any(), any(), any());

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

        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation
                .setRepositoryUri(new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, originRepoPath)).getURI().toString());
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // Mock the export methods to return valid resources
        var files = java.util.Map.of(".git/config", "[core]\nrepositoryformatversion = 0", ".git/HEAD", "ref: refs/heads/main", ".git/refs/heads/main",
                "1234567890abcdef1234567890abcdef12345678", ".git/objects/12/34567890abcdef1234567890abcdef12345678", "mock git object content", "test.txt", "test content");

        byte[] mockZipData = ZipTestUtil.createTestZipFile(files);
        InputStreamResource mockZipResource = ZipTestUtil.createMockZipResource(mockZipData, "mock-repo-with-git.zip");
        doReturn(mockZipResource).when(gitRepositoryExportService).exportInstructorRepositoryForExerciseInMemory(any(), any(), any());

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

}
