package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_BUILD_PLAN_CONFIGURATION_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_DOCKER_FLAGS_LENGTH;
import static de.tum.cit.aet.artemis.programming.util.ZipTestUtil.extractExerciseJsonFromZip;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCRepositoryTestService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTheiaConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class ProgrammingExerciseResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "programmingexerciseresource";

    /** The timeline a variant group owns in the tests below; its members must keep exactly these dates. */
    private static final ZonedDateTime GROUP_BASE_DATE = ZonedDateTime.parse("2099-01-01T00:00:00Z");

    private static final ZonedDateTime GROUP_RELEASE_DATE = GROUP_BASE_DATE.plusDays(1);

    private static final ZonedDateTime GROUP_START_DATE = GROUP_BASE_DATE.plusDays(2);

    private static final ZonedDateTime GROUP_DUE_DATE = GROUP_BASE_DATE.plusDays(7);

    private static final ZonedDateTime GROUP_ASSESSMENT_DUE_DATE = GROUP_BASE_DATE.plusDays(14);

    /** Stays with the exercise rather than the group; the update only re-derives it from the group's due date. */
    private static final ZonedDateTime EXERCISE_BUILD_AND_TEST_DATE = GROUP_DUE_DATE.plusHours(1);

    /** Title an update request sets alongside the (rejected) timeline change, to prove the rest of the update still lands. */
    private static final String RENAMED_VARIANT_TITLE = "Renamed variant";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LocalVCRepositoryTestService localVCRepositoryTestService;

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
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    @Autowired
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationTestRepository;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private CompetencyExerciseLinkTestRepository competencyExerciseLinkTestRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @AfterEach
    void tearDown() {
        // seedStudentRepositoryForParticipation registers the repositories it creates, so release them instead of letting the registry grow.
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);

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

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        seedTemplateRepository(programmingExercise);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-repository/" + RepositoryType.TEMPLATE.name(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        // Verify that file is a valid ZIP file
        assertThat(result[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(result[1]).isEqualTo((byte) 0x4B); // 'K'

        ZipTestUtil.verifyZipStructureAndContent(result);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportRepositoryWithFullHistory() throws Exception {

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        seedTemplateRepository(programmingExercise);

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

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testExportStudentRequestedSolutionRepository_shouldReturnZipWithoutGit() throws Exception {
        programmingExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(2));
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseTestService.setupExerciseForExport(programmingExercise);

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
        programmingExercise = programmingExerciseTestService.setupExerciseForExport(programmingExercise);

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
        var studentParticipation = participations.iterator().next();

        // Create and wire a LocalVC student repository, with a file in it so the export has something to return
        var studentRepository = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, studentParticipation);
        RepositoryExportTestUtil.writeFilesAndPush(studentRepository, Map.of("Submission.java", "public class Submission {}"), "Add student submission");
        programmingExerciseStudentParticipationTestRepository.save(studentParticipation);

        byte[] result = request.get(
                "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-student-repository?participationId=" + studentParticipation.getId(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
        ZipTestUtil.verifyZipStructureAndContent(result);
        ZipTestUtil.verifyZipDoesNotContainGitDirectory(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportedExerciseJsonWithCategories() throws Exception {
        /*
         * The factory method populateUnreleasedProgrammingExercise() will call
         * programmingExercise.setCategories(new HashSet<>(Set.of("cat1", "cat2"))).
         * We explicitly override those categories here with JSON-encoded strings
         * to verify that color information is preserved in the exported file.
         */
        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        Set<String> categoriesJson = Set.of("{\"color\":\"#0d3cc2\",\"category\":\"cat1\"}", "{\"color\":\"#691b0b\",\"category\":\"cat2\"}");
        programmingExercise.setCategories(new HashSet<>(categoriesJson));
        programmingExerciseRepository.save(programmingExercise);

        seedTemplateRepository(programmingExercise);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // WHEN
        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-exercise", HttpStatus.OK, byte[].class);

        // THEN
        assertThat(result).as("Export result should not be null").isNotNull();
        assertThat(result.length).as("Exported ZIP byte array should not be empty").isGreaterThan(0);

        String exerciseJson = extractExerciseJsonFromZip(result);
        assertThat(exerciseJson).as("Exported exercise JSON should not be blank").isNotBlank();

        var objectMapper = JsonObjectMapper.get();
        var json = objectMapper.readTree(exerciseJson);

        assertThat(json.has("categories")).as("Exported exercise JSON should contain a 'categories' field").isTrue();
        var categoriesArray = json.get("categories");
        assertThat(categoriesArray.isArray()).as("'categories' field should be an array").isTrue();
        assertThat(categoriesArray).as("Categories array should contain 2 entries").hasSize(2);

        // Parse inner JSON strings (since categories are stored as stringified JSON)
        List<String> categoryNames = new ArrayList<>();
        List<String> colors = new ArrayList<>();

        for (var node : categoriesArray) {
            var raw = node.asText();
            var inner = objectMapper.readTree(raw);
            categoryNames.add(inner.get("category").asText());
            colors.add(inner.get("color").asText());
        }

        // Verify category names
        assertThat(categoryNames).as("Exported categories should include the default names").containsExactlyInAnyOrder("cat1", "cat2");

        // Verify color values
        assertThat(colors).as("Exported categories should preserve color information").containsExactlyInAnyOrder("#0d3cc2", "#691b0b");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportedExerciseJsonWithoutCategories() throws Exception {
        // GIVEN
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(instructor, course, CourseRole.INSTRUCTOR);

        // Create a programming exercise and explicitly clear all categories
        // (The factory method populateUnreleasedProgrammingExercise() normally adds "cat1" and "cat2")
        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // ensure empty
        programmingExercise.setCategories(new HashSet<>());
        programmingExerciseRepository.save(programmingExercise);

        seedTemplateRepository(programmingExercise);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // WHEN
        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-exercise", HttpStatus.OK, byte[].class);

        // THEN
        assertThat(result).as("Export result should not be null").isNotNull();
        assertThat(result.length).as("Exported ZIP byte array should not be empty").isGreaterThan(0);

        String exerciseJson = extractExerciseJsonFromZip(result);
        assertThat(exerciseJson).as("Exported exercise JSON should not be blank").isNotBlank();

        var objectMapper = JsonObjectMapper.get();
        var json = objectMapper.readTree(exerciseJson);

        // Verify categories are not present
        assertThat(json.has("categories")).as("No 'categories' field should be present in exported JSON when exercise has none").isFalse();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProblemStatement_withTooLongProblemStatement_shouldReturnBadRequest() throws Exception {
        String tooLongProblemStatement = "a".repeat(100_001);

        request.patchWithResponseBody("/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement", tooLongProblemStatement, String.class,
                HttpStatus.BAD_REQUEST, MediaType.TEXT_PLAIN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testCreateProgrammingExercise_withTooLongProblemStatement_shouldReturnBadRequest() throws Exception {
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);

        var validPhases = new BuildPlanPhasesDTO(List.of(new BuildPhaseDTO("Compile", "./gradlew testClasses", BuildPhaseCondition.ALWAYS, false, List.of()),
                new BuildPhaseDTO("Test", "./gradlew test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"))), "ubuntu:latest");

        newExercise.getBuildConfig().setBuildPlanConfiguration(validPhases.toBuildPlanConfiguration());
        newExercise.setProblemStatement("a".repeat(100_001));

        request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_withTooLongProblemStatement_shouldReturnBadRequest() throws Exception {
        programmingExercise.setProblemStatement("a".repeat(100_001));

        request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_withTooLongBuildPlanConfiguration_shouldReturnBadRequest() throws Exception {
        addInstructorToCourse();

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        // A structurally valid phases configuration whose script pushes the serialized configuration past the maximum allowed length.
        // This ensures the request passes the build phase name parsing and is rejected specifically by the size validation.
        var oversizedPhase = new BuildPhaseDTO("Test", "a".repeat(MAX_BUILD_PLAN_CONFIGURATION_LENGTH + 1), BuildPhaseCondition.ALWAYS, false, List.of());
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(oversizedPhase), "ubuntu:latest").toBuildPlanConfiguration());

        request.putAndExpectError("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), HttpStatus.BAD_REQUEST,
                "buildPlanConfigurationTooLong");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_withTooLongDockerFlags_shouldReturnBadRequest() throws Exception {
        addInstructorToCourse();

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        // Structurally valid docker flags (parse successfully, each env variable below the per-variable limit) whose raw JSON
        // exceeds the maximum allowed length, so the request is rejected specifically by the size validation.
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(validBuildPlanConfiguration());
        programmingExercise.getBuildConfig().setDockerFlags(oversizedButValidDockerFlags());

        request.putAndExpectError("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), HttpStatus.BAD_REQUEST, "dockerFlagsTooLong");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testCreateProgrammingExercise_withTooLongBuildPlanConfiguration_shouldReturnBadRequest() throws Exception {
        addInstructorToCourse();

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);

        var oversizedPhase = new BuildPhaseDTO("Test", "a".repeat(MAX_BUILD_PLAN_CONFIGURATION_LENGTH + 1), BuildPhaseCondition.ALWAYS, false, List.of());
        newExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(oversizedPhase), "ubuntu:latest").toBuildPlanConfiguration());

        request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_preservesBuildAndTestDateOffset() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        // Setup exercise with an AFTER_DUE_DATE phase
        var phase = new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml"));
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        ZonedDateTime originalDueDate = ZonedDateTime.now().plusDays(2);
        ZonedDateTime originalBuildAndTestDate = originalDueDate.plusHours(1);

        programmingExercise.setDueDate(originalDueDate);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(originalBuildAndTestDate);
        programmingExerciseRepository.save(programmingExercise);

        // Update the due date (shift by +2 hours)
        ZonedDateTime newDueDate = originalDueDate.plusHours(2);
        programmingExercise.setDueDate(newDueDate);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);

        // Expected build and test date is shifted by +2 hours
        ZonedDateTime expectedBuildAndTestDate = originalBuildAndTestDate.plusHours(2);

        var updatedExercise = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class,
                HttpStatus.OK);

        var exerciseFromDb = programmingExerciseRepository.findByIdElseThrow(updatedExercise.getId());

        assertThat(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate()).isNotNull();
        assertThat(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate().toInstant()).as("buildAndTestStudentSubmissionsAfterDueDate should be shifted by the same offset")
                .isCloseTo(expectedBuildAndTestDate.toInstant(), within(1, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_preservesExamBuildAndTestDateOffset() throws Exception {
        programmingExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        var phase = new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml"));
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        ZonedDateTime examEndDate = ZonedDateTime.now().plusDays(2);
        int gracePeriodInSeconds = 60;
        var exam = programmingExercise.getExerciseGroup().getExam();
        exam.setEndDate(examEndDate);
        exam.setGracePeriod(gracePeriodInSeconds);
        examRepository.save(exam);

        ZonedDateTime originalReferenceDate = examEndDate.plusSeconds(gracePeriodInSeconds);
        ZonedDateTime expectedBuildAndTestDate = originalReferenceDate.plusHours(1);

        programmingExercise.setReleaseDate(null);
        programmingExercise.setDueDate(null);
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(expectedBuildAndTestDate);
        programmingExerciseRepository.save(programmingExercise);

        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);

        var updatedExercise = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class,
                HttpStatus.OK);

        var exerciseFromDb = programmingExerciseRepository.findByIdElseThrow(updatedExercise.getId());

        assertThat(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate()).isNotNull();
        assertThat(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate().toInstant())
                .as("buildAndTestStudentSubmissionsAfterDueDate should preserve the offset from the exam end date with grace")
                .isCloseTo(expectedBuildAndTestDate.toInstant(), within(1, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExerciseTimeline_preservesExamBuildAndTestDateOffset() throws Exception {
        programmingExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        var phase = new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml"));
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        ZonedDateTime examEndDate = ZonedDateTime.now().plusDays(2);
        int gracePeriodInSeconds = 60;
        var exam = programmingExercise.getExerciseGroup().getExam();
        exam.setEndDate(examEndDate);
        exam.setGracePeriod(gracePeriodInSeconds);
        examRepository.save(exam);

        ZonedDateTime originalReferenceDate = examEndDate.plusSeconds(gracePeriodInSeconds);
        ZonedDateTime expectedBuildAndTestDate = originalReferenceDate.plusHours(1);

        programmingExercise.setReleaseDate(null);
        programmingExercise.setDueDate(null);
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(expectedBuildAndTestDate);
        programmingExerciseRepository.save(programmingExercise);

        var updateDTO = new ProgrammingExerciseTimelineUpdateDTO(programmingExercise.getId(), programmingExercise.getReleaseDate(), programmingExercise.getStartDate(),
                programmingExercise.getDueDate(), programmingExercise.getAssessmentType(), programmingExercise.getAssessmentDueDate(),
                programmingExercise.getExampleSolutionPublicationDate(), null);

        var updatedExercise = request.putWithResponseBody("/api/programming/programming-exercises/timeline", updateDTO, ProgrammingExercise.class, HttpStatus.OK);

        var exerciseFromDb = programmingExerciseRepository.findByIdElseThrow(updatedExercise.getId());

        assertThat(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate()).isNotNull();
        assertThat(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate().toInstant())
                .as("buildAndTestStudentSubmissionsAfterDueDate should preserve the offset from the exam end date with grace")
                .isCloseTo(expectedBuildAndTestDate.toInstant(), within(1, java.time.temporal.ChronoUnit.SECONDS));
    }

    /**
     * The POST body is parsed into a DTO now. A DTO without an {@code id} component would let a client-supplied id
     * vanish silently through {@code ignoreUnknown} and turn the 400 into a successful creation, so the id has to
     * survive parsing and reach the validation. Raw JSON is used deliberately: a serialized entity cannot prove that
     * the DTO itself binds the key.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testCreateProgrammingExercise_rawJsonWithId_badRequest() throws Exception {
        addInstructorToCourse();

        String body = """
                {"id": 4711, "title": "Raw json exercise", "shortName": "rawjson", "packageName": "de.test", "maxPoints": 10.0,
                 "programmingLanguage": "JAVA", "projectType": "PLAIN_GRADLE", "staticCodeAnalysisEnabled": false,
                 "allowOnlineEditor": true, "allowOfflineIde": true, "course": {"id": %d}, "buildConfig": {}}
                """.formatted(course.getId());

        var response = request
                .performMvcRequest(MockMvcRequestBuilders.post(new URI("/api/programming/programming-exercises/setup")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        request.restoreSecurityContext();

        assertThat(response.getHeader("X-" + applicationName + "-error")).isEqualTo("error.idexists");
    }

    /**
     * The create form lets the author pick competencies and posts them with the exercise. The request DTO cannot bind
     * them itself (the links need managed competencies), so the resource resolves them through the competency link
     * service; without that call the exercise would be created with no links at all and nothing would fail loudly.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testCreateProgrammingExercise_withCompetencyLinks_persistsTheLinks() throws Exception {
        addInstructorToCourse();
        Competency competency = competencyUtilService.createCompetency(course);

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setShortName("compLinks");
        newExercise.setTitle("Exercise with competencies");
        newExercise.setChannelName("testchannel-competency");
        var validPhases = new BuildPlanPhasesDTO(List.of(new BuildPhaseDTO("Compile", "./gradlew testClasses", BuildPhaseCondition.ALWAYS, false, List.of()),
                new BuildPhaseDTO("Test", "./gradlew test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"))), "ubuntu:latest");
        newExercise.getBuildConfig().setBuildPlanConfiguration(validPhases.toBuildPlanConfiguration());
        newExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, newExercise, 1)));

        var created = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExerciseResponseDTO.class, HttpStatus.CREATED);

        // read the rows back, not the in-memory graph: the links are persisted only after the exercise has an id
        List<CompetencyExerciseLink> storedLinks = competencyExerciseLinkTestRepository.findByExerciseIdWithCompetency(created.id());
        assertThat(storedLinks).hasSize(1);
        assertThat(storedLinks.getFirst().getCompetency().getId()).isEqualTo(competency.getId());
        assertThat(storedLinks.getFirst().getWeight()).isEqualTo(1);
    }

    /**
     * The response of the update endpoint is the object the client rebuilds its next request body from. This test pins
     * the full traced read contract for a course exercise.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_responseCarriesClientReadContract_courseExercise() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        var response = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise),
                ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(response.id()).isEqualTo(programmingExercise.getId());
        assertThat(response.type()).isEqualTo(ProgrammingExerciseResponseDTO.TYPE);
        assertThat(response.title()).isEqualTo(programmingExercise.getTitle());
        assertThat(response.shortName()).isEqualTo(programmingExercise.getShortName());
        assertThat(response.programmingLanguage()).isEqualTo(programmingExercise.getProgrammingLanguage());
        assertThat(response.maxPoints()).isEqualTo(programmingExercise.getMaxPoints());
        assertThat(response.projectKey()).isEqualTo(programmingExercise.getProjectKey());
        assertThat(response.assessmentType()).isEqualTo(programmingExercise.getAssessmentType());
        assertThat(response.includedInOverallScore()).isEqualTo(programmingExercise.getIncludedInOverallScore());
        assertThat(response.testRepositoryUri()).isEqualTo(programmingExercise.getTestRepositoryUri());
        // the nested course is mandatory: the client renders display links off it, not off a flat course id
        assertThat(response.course()).isNotNull();
        assertThat(response.course().id()).isEqualTo(course.getId());
        assertThat(response.course().title()).isEqualTo(course.getTitle());
        assertThat(response.course().shortName()).isEqualTo(course.getShortName());
        assertThat(response.exerciseGroup()).isNull();
    }

    /**
     * The exam variant of the read contract: the client recomputes the exercise group from the response and the exam
     * navigation walks {@code exerciseGroup.exam.course}. A flat id silently breaks saving an exam exercise.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_responseCarriesClientReadContract_examExercise() throws Exception {
        programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        course = programmingExercise.getExerciseGroup().getExam().getCourse();
        addInstructorToCourse();
        var exerciseGroup = programmingExercise.getExerciseGroup();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        clearDatesOfExamExercise();

        var response = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise),
                ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(response.course()).isNull();
        assertThat(response.exerciseGroup()).isNotNull();
        assertThat(response.exerciseGroup().id()).isEqualTo(exerciseGroup.getId());
        assertThat(response.exerciseGroup().exam()).isNotNull();
        assertThat(response.exerciseGroup().exam().id()).isEqualTo(exerciseGroup.getExam().getId());
        assertThat(response.exerciseGroup().exam().title()).isEqualTo(exerciseGroup.getExam().getTitle());
        assertThat(response.exerciseGroup().exam().course()).isNotNull();
        assertThat(response.exerciseGroup().exam().course().id()).isEqualTo(course.getId());
        assertThat(response.exerciseGroup().exam().course().title()).isEqualTo(course.getTitle());
    }

    /**
     * A green server suite cannot see the client request-builder: server tests build the update body from the full
     * entity, which the client never has. This test rebuilds the body from ONLY the fields the response DTO exposes -
     * the way {@code toUpdateProgrammingExerciseDTO} does - and saves again, for a course and an exam exercise.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_clientShapedBodyRebuiltFromResponse_courseExercise() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        var firstResponse = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise),
                ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        var clientBody = toClientUpdateDTO(firstResponse);
        var secondResponse = request.putWithResponseBody("/api/programming/programming-exercises", clientBody, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(secondResponse.id()).isEqualTo(programmingExercise.getId());
        assertThat(secondResponse.title()).isEqualTo(firstResponse.title());
        assertThat(secondResponse.course()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_clientShapedBodyRebuiltFromResponse_examExercise() throws Exception {
        programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        course = programmingExercise.getExerciseGroup().getExam().getCourse();
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        clearDatesOfExamExercise();

        var firstResponse = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise),
                ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        var clientBody = toClientUpdateDTO(firstResponse);
        var secondResponse = request.putWithResponseBody("/api/programming/programming-exercises", clientBody, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(secondResponse.exerciseGroup()).isNotNull();
        assertThat(secondResponse.exerciseGroup().exam().course().id()).isEqualTo(course.getId());
    }

    /**
     * Grading criteria are cascade-ALL with orphanRemoval: an update carrying an empty collection must delete the
     * remaining rows, and the ids of surviving criteria must not be regenerated.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_emptyGradingCriteria_deletesAllCriterionRows() throws Exception {
        addInstructorToCourse();
        exerciseUtilService.addGradingInstructionsToExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        var criteriaBefore = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
        assertThat(criteriaBefore).hasSize(3);
        var criterionIdsBefore = criteriaBefore.stream().map(criterion -> criterion.getId()).collect(java.util.stream.Collectors.toSet());

        // first save the unchanged criteria: the ids must survive the round trip, otherwise the next save orphans them
        var updateDTO = UpdateProgrammingExerciseDTO.of(programmingExercise);
        request.putWithResponseBody("/api/programming/programming-exercises", updateDTO, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);
        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId())).extracting(criterion -> criterion.getId())
                .containsExactlyInAnyOrderElementsOf(criterionIdsBefore);

        // now delete the last criteria by sending an empty collection (the clear() branch)
        request.putWithResponseBody("/api/programming/programming-exercises", withGradingCriteria(updateDTO, Set.of()), ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId())).isEmpty();
    }

    /**
     * {@code SubmissionPolicyDTO.toEntity()} copies the id through, otherwise the transient policy the update path
     * assigns to the managed exercise inserts a second {@code submission_policy} row.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_resentSubmissionPolicy_keepsExactlyOneRowWithSameId() throws Exception {
        addInstructorToCourse();
        var policy = new LockRepositoryPolicy();
        policy.setSubmissionLimit(5);
        policy.setActive(true);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(policy, programmingExercise);
        long policyId = policy.getId();

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        // the submission policy is a lazy relation of the loaded exercise; re-attach the loaded one the way the client
        // re-sends the policy object it received
        programmingExercise.setSubmissionPolicy(submissionPolicyRepository.findByIdElseThrow(policyId));
        var updateDTO = UpdateProgrammingExerciseDTO.of(programmingExercise);
        assertThat(updateDTO.submissionPolicy()).isNotNull();
        assertThat(updateDTO.submissionPolicy().id()).isEqualTo(policyId);
        assertThat(updateDTO.submissionPolicy().type()).isEqualTo(SubmissionPolicyDTO.TYPE_LOCK_REPOSITORY);

        // the client re-sends the loaded policy on every save - the id must survive so no second row is inserted
        request.putWithResponseBody("/api/programming/programming-exercises", updateDTO, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);
        request.putWithResponseBody("/api/programming/programming-exercises", updateDTO, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        // scope the row count to this exercise: the submission_policy table is shared with every other test in the run
        assertThat(submissionPolicyRepository.findAllByProgrammingExerciseIds(Set.of(programmingExercise.getId()))).extracting(entry -> entry.getId()).containsExactly(policyId);
        var exerciseFromDb = programmingExerciseRepository.findWithSubmissionPolicyById(programmingExercise.getId()).orElseThrow();
        assertThat(exerciseFromDb.getSubmissionPolicy().getId()).isEqualTo(policyId);
        assertThat(exerciseFromDb.getSubmissionPolicy().getSubmissionLimit()).isEqualTo(5);
    }

    /**
     * Auxiliary repositories are an {@code @OrderColumn} collection whose entity helper sets the back reference. A
     * write path that assigns the collection directly leaves {@code aux_repo.exercise_id} null.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_auxiliaryRepositories_keepIdsAndBackReference() throws Exception {
        addInstructorToCourse();
        var auxRepository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(programmingExercise);
        long auxRepositoryId = auxRepository.getId();

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        // re-attach the loaded auxiliary repositories the way the client re-sends the ones it received
        programmingExercise.setAuxiliaryRepositories(new ArrayList<>(auxiliaryRepositoryRepository.findByExerciseId(programmingExercise.getId())));
        var updateDTO = UpdateProgrammingExerciseDTO.of(programmingExercise);
        assertThat(updateDTO.auxiliaryRepositories()).extracting(repository -> repository.id()).containsExactly(auxRepositoryId);

        request.putWithResponseBody("/api/programming/programming-exercises", updateDTO, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);
        request.putWithResponseBody("/api/programming/programming-exercises", updateDTO, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        // findByExerciseId only returns rows whose exercise_id column is set, so this also asserts the back reference
        assertThat(auxiliaryRepositoryRepository.findByExerciseId(programmingExercise.getId())).extracting(repository -> repository.getId()).containsExactly(auxRepositoryId);
    }

    /**
     * The re-evaluate response is fed back into the next save. If the criterion or instruction ids were regenerated or
     * dropped, the following save would orphan (and thereby delete) every structured grading instruction.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testReEvaluateProgrammingExercise_gradingCriterionIdsAreStable() throws Exception {
        addInstructorToCourse();
        exerciseUtilService.addGradingInstructionsToExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        var criterionIdsBefore = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId()).stream().map(criterion -> criterion.getId())
                .collect(java.util.stream.Collectors.toSet());
        var instructionIdsBefore = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId()).stream()
                .flatMap(criterion -> criterion.getStructuredGradingInstructions().stream()).map(instruction -> instruction.getId()).collect(java.util.stream.Collectors.toSet());

        var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
        params.add("deleteFeedback", "false");
        var response = request.putWithResponseBodyAndParams("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate",
                UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExerciseResponseDTO.class, HttpStatus.OK, params);

        assertThat(response.id()).isEqualTo(programmingExercise.getId());
        var criteriaAfter = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
        assertThat(criteriaAfter).extracting(criterion -> criterion.getId()).containsExactlyInAnyOrderElementsOf(criterionIdsBefore);
        assertThat(criteriaAfter).flatExtracting(criterion -> criterion.getStructuredGradingInstructions()).extracting(instruction -> instruction.getId())
                .containsExactlyInAnyOrderElementsOf(instructionIdsBefore);
    }

    /**
     * The problem-statement PATCH keeps a text/plain request body and returns the exercise with the problem statement
     * after the test ids were replaced by test names, plus the update alert header.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProblemStatement_responseCarriesClientReadContract() throws Exception {
        addInstructorToCourse();
        final String newProblemStatement = "a brand new problem statement";

        var response = request
                .performMvcRequest(MockMvcRequestBuilders.patch(new URI("/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement"))
                        .contentType(MediaType.TEXT_PLAIN).content(newProblemStatement))
                .andExpect(status().isOk()).andReturn().getResponse();
        request.restoreSecurityContext();

        assertThat(response.getHeader("X-" + applicationName + "-alert")).isNotNull();
        var body = JsonObjectMapper.get().readValue(response.getContentAsString(), ProgrammingExerciseResponseDTO.class);
        assertThat(body.id()).isEqualTo(programmingExercise.getId());
        assertThat(body.type()).isEqualTo(ProgrammingExerciseResponseDTO.TYPE);
        assertThat(body.problemStatement()).isEqualTo(newProblemStatement);
        assertThat(body.title()).isEqualTo(programmingExercise.getTitle());
        assertThat(body.course()).isNotNull();
        assertThat(body.course().id()).isEqualTo(course.getId());
    }

    /**
     * The timeline response feeds the same client pipeline as the full update, and the server recomputes
     * {@code buildAndTestStudentSubmissionsAfterDueDate}, so the recomputed value has to be on the wire.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExerciseTimeline_responseCarriesClientReadContract() throws Exception {
        addInstructorToCourse();
        ZonedDateTime newDueDate = ZonedDateTime.now().plusDays(3);

        var updateDTO = new ProgrammingExerciseTimelineUpdateDTO(programmingExercise.getId(), programmingExercise.getReleaseDate(), programmingExercise.getStartDate(), newDueDate,
                programmingExercise.getAssessmentType(), null, programmingExercise.getExampleSolutionPublicationDate(), null);

        var response = request.putWithResponseBody("/api/programming/programming-exercises/timeline", updateDTO, ProgrammingExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(response.id()).isEqualTo(programmingExercise.getId());
        assertThat(response.type()).isEqualTo(ProgrammingExerciseResponseDTO.TYPE);
        assertThat(response.title()).isEqualTo(programmingExercise.getTitle());
        assertThat(response.dueDate()).isNotNull();
        assertThat(response.dueDate().toInstant()).isCloseTo(newDueDate.toInstant(), within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(response.course()).isNotNull();
        assertThat(response.course().id()).isEqualTo(course.getId());

        var exerciseFromDb = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
        // the recomputed value must be the one the client receives, not the one it sent
        if (exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate() == null) {
            assertThat(response.buildAndTestStudentSubmissionsAfterDueDate()).isNull();
        }
        else {
            assertThat(response.buildAndTestStudentSubmissionsAfterDueDate().toInstant()).isCloseTo(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate().toInstant(),
                    within(1, java.time.temporal.ChronoUnit.SECONDS));
        }
    }

    /**
     * The DTO mapper must not pull additional sub-graphs (test cases, tasks, plagiarism cases) into the update path.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_doesNotFanOutQueries() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        var updateDTO = UpdateProgrammingExerciseDTO.of(programmingExercise);

        assertThatDb(() -> request.putWithResponseBody("/api/programming/programming-exercises", updateDTO, ProgrammingExerciseResponseDTO.class, HttpStatus.OK))
                .hasBeenCalledAtMostTimes(70);
    }

    /**
     * Rebuilds an update request body the way the Angular client does: from the loaded response object only. Any field
     * missing from {@link ProgrammingExerciseResponseDTO} is therefore missing from the request as well.
     */
    private static UpdateProgrammingExerciseDTO toClientUpdateDTO(ProgrammingExerciseResponseDTO response) {
        Long exerciseGroupId = response.exerciseGroup() != null ? response.exerciseGroup().id() : null;
        Long courseId = exerciseGroupId != null ? null : (response.course() != null ? response.course().id() : null);
        return new UpdateProgrammingExerciseDTO(response.id(), response.title(), response.channelName(), response.shortName(), response.problemStatement(), response.categories(),
                response.difficulty(), response.maxPoints(), response.bonusPoints(), response.includedInOverallScore(), response.allowComplaintsForAutomaticAssessments(),
                response.allowFeedbackRequests(), response.presentationScoreEnabled(), response.secondCorrectionEnabled(), response.feedbackSuggestionModule(),
                response.gradingInstructions(), response.releaseDate(), response.startDate(), response.dueDate(), response.assessmentDueDate(),
                response.exampleSolutionPublicationDate(), courseId, exerciseGroupId, response.gradingCriteria(), response.competencyLinks(), response.testRepositoryUri(), null,
                response.auxiliaryRepositories(), response.allowOnlineEditor(), response.allowOfflineIde(), Boolean.TRUE.equals(response.allowOnlineIde()),
                response.staticCodeAnalysisEnabled(), response.maxStaticCodeAnalysisPenalty(), response.programmingLanguage(), response.packageName(),
                Boolean.TRUE.equals(response.showTestNamesToStudents()), response.buildAndTestStudentSubmissionsAfterDueDate(), response.testCasesChanged(), response.projectKey(),
                response.submissionPolicy(), response.projectType(), Boolean.TRUE.equals(response.releaseTestsWithExampleSolution()), response.assessmentType(),
                response.buildConfig());
    }

    /**
     * Exam exercises must not carry their own dates - they inherit the exam timeline - so the update validation
     * rejects them. Mirrors what the exam edit form sends.
     */
    private void clearDatesOfExamExercise() {
        programmingExercise.setReleaseDate(null);
        programmingExercise.setStartDate(null);
        programmingExercise.setDueDate(null);
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise.setExampleSolutionPublicationDate(null);
        programmingExerciseRepository.save(programmingExercise);
    }

    private static UpdateProgrammingExerciseDTO withGradingCriteria(UpdateProgrammingExerciseDTO dto, Set<GradingCriterionDTO> gradingCriteria) {
        return new UpdateProgrammingExerciseDTO(dto.id(), dto.title(), dto.channelName(), dto.shortName(), dto.problemStatement(), dto.categories(), dto.difficulty(),
                dto.maxPoints(), dto.bonusPoints(), dto.includedInOverallScore(), dto.allowComplaintsForAutomaticAssessments(), dto.allowFeedbackRequests(),
                dto.presentationScoreEnabled(), dto.secondCorrectionEnabled(), dto.feedbackSuggestionModule(), dto.gradingInstructions(), dto.releaseDate(), dto.startDate(),
                dto.dueDate(), dto.assessmentDueDate(), dto.exampleSolutionPublicationDate(), dto.courseId(), dto.exerciseGroupId(), gradingCriteria, dto.competencyLinks(),
                dto.testRepositoryUri(), dto.solutionRepositoryUri(), dto.auxiliaryRepositories(), dto.allowOnlineEditor(), dto.allowOfflineIde(), dto.allowOnlineIde(),
                dto.staticCodeAnalysisEnabled(), dto.maxStaticCodeAnalysisPenalty(), dto.programmingLanguage(), dto.packageName(), dto.showTestNamesToStudents(),
                dto.buildAndTestStudentSubmissionsAfterDueDate(), dto.testCasesChanged(), dto.projectKey(), dto.submissionPolicy(), dto.projectType(),
                dto.releaseTestsWithExampleSolution(), dto.assessmentType(), dto.buildConfig());
    }

    /**
     * Writes a file into the template repository of the exercise, so that exporting it produces a non-empty archive. The repository itself was already created together with
     * the template participation.
     */
    private void seedTemplateRepository(ProgrammingExercise exercise) {
        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        localVCRepositoryTestService.writeFilesAndPush(new LocalVCRepositoryUri(templateParticipation.getRepositoryUri()), Map.of("README.md", "Initial commit"), "Initial commit");
    }

    private String validBuildPlanConfiguration() throws JsonProcessingException {
        var phase = new BuildPhaseDTO("Test", "echo test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"));
        return new BuildPlanPhasesDTO(List.of(phase), "ubuntu:latest").toBuildPlanConfiguration();
    }

    /**
     * Builds a structurally valid docker flags JSON whose raw length exceeds MAX_DOCKER_FLAGS_LENGTH.
     * Each environment variable stays below the per-variable length limit, so it passes the docker flags parsing and is
     * rejected solely by the build config size validation.
     *
     * @return an oversized but otherwise valid docker flags JSON string
     */
    private String oversizedButValidDockerFlags() {
        StringBuilder env = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i > 0) {
                env.append(",");
            }
            env.append("\"key").append(i).append("\":\"").append("v".repeat(900)).append("\"");
        }
        String dockerFlags = "{\"cpuCount\": 4, \"memory\": 3072, \"memorySwap\": 2048, \"env\": {" + env + "}}";
        assertThat(dockerFlags.length()).isGreaterThan(MAX_DOCKER_FLAGS_LENGTH);
        return dockerFlags;
    }

    private void addInstructorToCourse() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(instructor, course, CourseRole.INSTRUCTOR);
    }

    /**
     * A variant group owns the shared timeline of its members, but the general update endpoint applies the request's dates
     * straight onto the managed entity — so without a server-side guard a stale client or a direct request could
     * desynchronize a member from its group, changing student availability and build scheduling. The dates here are
     * incidental among many other fields, so the server overwrites them from the group rather than rejecting the request:
     * the unrelated part of the edit still lands, and the member's timeline self-heals.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExerciseCannotChangeVariantGroupTimeline() throws Exception {
        attachProgrammingExerciseToVariantGroup();
        requestTimelineShiftAlongsideUnownedChange();

        request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class, HttpStatus.OK);

        assertTimelinePinnedToGroup();
    }

    /**
     * The re-evaluate endpoint runs the same field-copy step as the general update before persisting, so it needs the
     * same guard: otherwise re-evaluating a grouped exercise is a second way to desynchronize its timeline.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testReEvaluateProgrammingExerciseCannotChangeVariantGroupTimeline() throws Exception {
        attachProgrammingExerciseToVariantGroup();
        requestTimelineShiftAlongsideUnownedChange();

        request.putWithResponseBody("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate?deleteFeedback=false",
                UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class, HttpStatus.OK);

        assertTimelinePinnedToGroup();
    }

    // The /timeline guard for group members is covered by ExerciseVariantGroupIntegrationTest.

    /** Puts {@link #programmingExercise} into a variant group whose timeline it already matches. */
    private void attachProgrammingExerciseToVariantGroup() throws JsonProcessingException {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setTitle("Loop variants");
        group.setReleaseDate(GROUP_RELEASE_DATE);
        group.setStartDate(GROUP_START_DATE);
        group.setDueDate(GROUP_DUE_DATE);
        group.setAssessmentDueDate(GROUP_ASSESSMENT_DUE_DATE);

        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        // Without an AFTER_DUE_DATE phase the automatic service clears the build-and-test date on every update, which
        // would hide whether the group guard leaves that exercise-owned date alone.
        var phase = new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml"));
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        programmingExercise.setExerciseVariantGroup(exerciseVariantGroupRepository.save(group));
        programmingExercise.setReleaseDate(GROUP_RELEASE_DATE);
        programmingExercise.setStartDate(GROUP_START_DATE);
        programmingExercise.setDueDate(GROUP_DUE_DATE);
        programmingExercise.setAssessmentDueDate(GROUP_ASSESSMENT_DUE_DATE);
        // The group does not own this date, so it stays with the exercise (re-derived from the group's due date).
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(EXERCISE_BUILD_AND_TEST_DATE);
        programmingExerciseRepository.save(programmingExercise);
    }

    /** Moves every group-owned date on the in-memory exercise, alongside a change the group does not own. */
    private void requestTimelineShiftAlongsideUnownedChange() {
        programmingExercise.setTitle(RENAMED_VARIANT_TITLE);
        programmingExercise.setReleaseDate(GROUP_RELEASE_DATE.plusDays(2));
        programmingExercise.setStartDate(GROUP_START_DATE.plusDays(2));
        programmingExercise.setDueDate(GROUP_DUE_DATE.plusDays(2));
        programmingExercise.setAssessmentDueDate(GROUP_ASSESSMENT_DUE_DATE.plusDays(2));
    }

    private void assertTimelinePinnedToGroup() {
        var exerciseFromDb = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
        assertThat(exerciseFromDb.getReleaseDate().toInstant()).as("the group's release date wins over the request's").isCloseTo(GROUP_RELEASE_DATE.toInstant(),
                within(1, SECONDS));
        assertThat(exerciseFromDb.getStartDate().toInstant()).as("the group's start date wins over the request's").isCloseTo(GROUP_START_DATE.toInstant(), within(1, SECONDS));
        assertThat(exerciseFromDb.getDueDate().toInstant()).as("the group's due date wins over the request's").isCloseTo(GROUP_DUE_DATE.toInstant(), within(1, SECONDS));
        assertThat(exerciseFromDb.getAssessmentDueDate().toInstant()).as("the group's assessment due date wins over the request's").isCloseTo(GROUP_ASSESSMENT_DUE_DATE.toInstant(),
                within(1, SECONDS));
        assertThat(exerciseFromDb.getBuildAndTestStudentSubmissionsAfterDueDate().toInstant()).as("the exercise-owned build-and-test date is left alone")
                .isCloseTo(EXERCISE_BUILD_AND_TEST_DATE.toInstant(), within(1, SECONDS));
        assertThat(exerciseFromDb.getTitle()).as("the rest of the update still applies").isEqualTo(RENAMED_VARIANT_TITLE);
    }

}
