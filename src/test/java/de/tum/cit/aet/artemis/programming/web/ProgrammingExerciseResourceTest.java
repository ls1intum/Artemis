package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.programming.util.ZipTestUtil.extractExerciseJsonFromZip;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
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
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTheiaConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
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

    /**
     * Fixed timeline for exercises built as request payloads below. The dates only have to satisfy the ordering rules of
     * {@code validateDates()}, so pinning them keeps the payload independent of the wall clock.
     */
    private static final ZonedDateTime PAYLOAD_RELEASE_DATE = ZonedDateTime.parse("2099-02-01T00:00:00Z");

    private static final ZonedDateTime PAYLOAD_DUE_DATE = PAYLOAD_RELEASE_DATE.plusDays(8);

    @Autowired
    private UserUtilService userUtilService;

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

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

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

        // Create and wire a LocalVC student repository via util
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, studentParticipation);
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

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = tempPath.resolve("testOriginRepoCategories");
        localRepo.configureRepos(originRepoPath, "testLocalRepoCategories", "testOriginRepoCategories");
        setupLocalVCRepository(localRepo, programmingExercise);

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

        localRepo.resetLocalRepo();
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

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = tempPath.resolve("testOriginRepoNoCategories");
        localRepo.configureRepos(originRepoPath, "testLocalRepoNoCategories", "testOriginRepoNoCategories");
        setupLocalVCRepository(localRepo, programmingExercise);

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

        localRepo.resetLocalRepo();
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
    void testCreateProgrammingExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        // ProgrammingExercise create still receives the raw entity, so the submitted plagiarism config reaches validation
        // directly. This path is intentionally left unchanged; the test guards that invalid values are still rejected.
        addInstructorToCourse();

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(PAYLOAD_RELEASE_DATE, PAYLOAD_DUE_DATE, course);
        var validPhases = new BuildPlanPhasesDTO(List.of(new BuildPhaseDTO("Compile", "./gradlew testClasses", BuildPhaseCondition.ALWAYS, false, List.of()),
                new BuildPhaseDTO("Test", "./gradlew test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"))), "ubuntu:latest");
        newExercise.getBuildConfig().setBuildPlanConfiguration(validPhases.toBuildPlanConfiguration());

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(-1); // invalid: below 0
        config.setMinimumScore(50);
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        newExercise.setPlagiarismDetectionConfig(config);

        request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_validPlagiarismDetectionConfig_updatesExistingConfigInPlace() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        setValidBuildPlanConfiguration();

        PlagiarismDetectionConfig config = ensurePlagiarismDetectionConfig(programmingExercise);
        config.setSimilarityThreshold(30);
        config.setMinimumScore(5);
        config.setMinimumSize(10);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(10);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        Long configId = programmingExercise.getPlagiarismDetectionConfig().getId();
        assertThat(configId).isNotNull();

        programmingExercise.getPlagiarismDetectionConfig().setSimilarityThreshold(66);
        programmingExercise.getPlagiarismDetectionConfig().setMinimumScore(9);
        var updated = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class,
                HttpStatus.OK);

        var fromDb = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(updated.getId()).orElseThrow();
        // Same config row is mutated in place (no duplicate row), and the new values are persisted.
        assertThat(fromDb.getPlagiarismDetectionConfig().getId()).isEqualTo(configId);
        assertThat(fromDb.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(66);
        assertThat(fromDb.getPlagiarismDetectionConfig().getMinimumScore()).isEqualTo(9);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        setValidBuildPlanConfiguration();

        PlagiarismDetectionConfig config = ensurePlagiarismDetectionConfig(programmingExercise);
        // Persist a valid baseline first, so a 400 can only stem from the submitted (invalid) config, not the stored one.
        config.setContinuousPlagiarismControlEnabled(true);
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(false);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(9);
        config.setSimilarityThreshold(50);
        config.setMinimumScore(4);
        config.setMinimumSize(12);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        Long configId = programmingExercise.getPlagiarismDetectionConfig().getId();
        assertThat(configId).isNotNull();

        programmingExercise.getPlagiarismDetectionConfig().setSimilarityThreshold(101); // invalid: above 100
        request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class,
                HttpStatus.BAD_REQUEST);

        ProgrammingExercise reloaded = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId())
                .orElseThrow();
        PlagiarismDetectionConfig reloadedConfig = reloaded.getPlagiarismDetectionConfig();
        assertThat(reloadedConfig).isNotNull();
        assertThat(reloadedConfig.getId()).isEqualTo(configId);
        assertThat(reloadedConfig.isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(reloadedConfig.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isFalse();
        assertThat(reloadedConfig.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(9);
        assertThat(reloadedConfig.getSimilarityThreshold()).isEqualTo(50);
        assertThat(reloadedConfig.getMinimumScore()).isEqualTo(4);
        assertThat(reloadedConfig.getMinimumSize()).isEqualTo(12);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testReEvaluateAndUpdateProgrammingExercise_invalidPlagiarismDetectionConfig_badRequestAndPreservesExistingConfig() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        setValidBuildPlanConfiguration();

        PlagiarismDetectionConfig config = ensurePlagiarismDetectionConfig(programmingExercise);
        config.setContinuousPlagiarismControlEnabled(true);
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(false);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(11);
        config.setSimilarityThreshold(45);
        config.setMinimumScore(6);
        config.setMinimumSize(14);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        Long configId = programmingExercise.getPlagiarismDetectionConfig().getId();
        assertThat(configId).isNotNull();

        programmingExercise.getPlagiarismDetectionConfig().setSimilarityThreshold(101); // invalid: above 100
        request.putWithResponseBody("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate?deleteFeedback=false",
                UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class, HttpStatus.BAD_REQUEST);

        ProgrammingExercise reloaded = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId())
                .orElseThrow();
        PlagiarismDetectionConfig reloadedConfig = reloaded.getPlagiarismDetectionConfig();
        assertThat(reloadedConfig).isNotNull();
        assertThat(reloadedConfig.getId()).isEqualTo(configId);
        assertThat(reloadedConfig.isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(reloadedConfig.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isFalse();
        assertThat(reloadedConfig.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(11);
        assertThat(reloadedConfig.getSimilarityThreshold()).isEqualTo(45);
        assertThat(reloadedConfig.getMinimumScore()).isEqualTo(6);
        assertThat(reloadedConfig.getMinimumSize()).isEqualTo(14);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_omittedPlagiarismDetectionConfig_preservesExisting() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        setValidBuildPlanConfiguration();

        PlagiarismDetectionConfig config = ensurePlagiarismDetectionConfig(programmingExercise);
        config.setSimilarityThreshold(37);
        config.setMinimumScore(4);
        config.setMinimumSize(9);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(12);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        Long configId = programmingExercise.getPlagiarismDetectionConfig().getId();

        // Simulate a caller that does not send the field: rebuild the same DTO but with a null plagiarism config.
        UpdateProgrammingExerciseDTO base = UpdateProgrammingExerciseDTO.of(programmingExercise);
        UpdateProgrammingExerciseDTO withoutConfig = new UpdateProgrammingExerciseDTO(base.id(), base.title(), base.channelName(), base.shortName(), base.problemStatement(),
                base.categories(), base.difficulty(), base.maxPoints(), base.bonusPoints(), base.includedInOverallScore(), base.allowComplaintsForAutomaticAssessments(),
                base.allowFeedbackRequests(), base.presentationScoreEnabled(), base.secondCorrectionEnabled(), base.feedbackSuggestionModule(), base.gradingInstructions(),
                base.releaseDate(), base.startDate(), base.dueDate(), base.assessmentDueDate(), base.exampleSolutionPublicationDate(), base.courseId(), base.exerciseGroupId(),
                base.gradingCriteria(), base.competencyLinks(), base.testRepositoryUri(), base.solutionRepositoryUri(), base.auxiliaryRepositories(), base.allowOnlineEditor(),
                base.allowOfflineIde(), base.allowOnlineIde(), base.staticCodeAnalysisEnabled(), base.maxStaticCodeAnalysisPenalty(), base.programmingLanguage(),
                base.packageName(), base.showTestNamesToStudents(), base.buildAndTestStudentSubmissionsAfterDueDate(), base.testCasesChanged(), base.projectKey(),
                base.submissionPolicy(), base.projectType(), base.releaseTestsWithExampleSolution(), base.assessmentType(), base.buildConfig(), null);
        var updated = request.putWithResponseBody("/api/programming/programming-exercises", withoutConfig, ProgrammingExercise.class, HttpStatus.OK);

        var fromDb = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(updated.getId()).orElseThrow();
        // The existing config is preserved unchanged when the DTO omits it.
        assertThat(fromDb.getPlagiarismDetectionConfig()).isNotNull();
        assertThat(fromDb.getPlagiarismDetectionConfig().getId()).isEqualTo(configId);
        assertThat(fromDb.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(37);
        assertThat(fromDb.getPlagiarismDetectionConfig().getMinimumScore()).isEqualTo(4);
        assertThat(fromDb.getPlagiarismDetectionConfig().getMinimumSize()).isEqualTo(9);
        assertThat(fromDb.getPlagiarismDetectionConfig().getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(12);
    }

    /**
     * The other update tests all start from an exercise that already owns a config, so they only cover the in-place
     * branch of {@code PlagiarismDetectionConfigHelper.applyToExercise}. This one starts with no association at all, so
     * it covers the branch that builds a new config from the DTO and persists it through the exercise cascade.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testUpdateProgrammingExercise_missingPlagiarismDetectionConfig_createsFromSubmittedValues() throws Exception {
        prepareExerciseWithoutPlagiarismDetectionConfig();
        programmingExercise.setPlagiarismDetectionConfig(submittedPlagiarismDetectionConfig());

        var updated = request.putWithResponseBody("/api/programming/programming-exercises", UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class,
                HttpStatus.OK);

        assertCreatedPlagiarismDetectionConfig(updated.getId());
    }

    /**
     * Re-evaluate runs the same apply-and-persist step as the general update, so the create branch must hold there too.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testReEvaluateProgrammingExercise_missingPlagiarismDetectionConfig_createsFromSubmittedValues() throws Exception {
        prepareExerciseWithoutPlagiarismDetectionConfig();
        programmingExercise.setPlagiarismDetectionConfig(submittedPlagiarismDetectionConfig());

        var updated = request.putWithResponseBody("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate?deleteFeedback=false",
                UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class, HttpStatus.OK);

        assertCreatedPlagiarismDetectionConfig(updated.getId());
    }

    /**
     * Loads {@link #programmingExercise}, gives it a valid build plan configuration and drops its plagiarism detection
     * config, asserting that the association really is gone before the request under test runs.
     */
    private void prepareExerciseWithoutPlagiarismDetectionConfig() throws Exception {
        addInstructorToCourse();
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        setValidBuildPlanConfiguration();

        programmingExercise.setPlagiarismDetectionConfig(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        assertThat(programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow()
                .getPlagiarismDetectionConfig()).as("the exercise must start without a plagiarism detection config").isNull();
    }

    /** The (valid) values the request submits for an exercise that has no plagiarism detection config yet. */
    private PlagiarismDetectionConfig submittedPlagiarismDetectionConfig() {
        var config = new PlagiarismDetectionConfig();
        config.setContinuousPlagiarismControlEnabled(true);
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(true);
        config.setSimilarityThreshold(55);
        config.setMinimumScore(8);
        config.setMinimumSize(16);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(13);
        return config;
    }

    /** Asserts that the exercise now owns a persisted config carrying exactly the submitted values. */
    private void assertCreatedPlagiarismDetectionConfig(long exerciseId) {
        var fromDb = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(exerciseId).orElseThrow();
        PlagiarismDetectionConfig createdConfig = fromDb.getPlagiarismDetectionConfig();
        assertThat(createdConfig).as("the resource must create the missing config").isNotNull();
        assertThat(createdConfig.getId()).as("the created config must be persisted").isNotNull();
        assertThat(createdConfig.isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(createdConfig.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isTrue();
        assertThat(createdConfig.getSimilarityThreshold()).isEqualTo(55);
        assertThat(createdConfig.getMinimumScore()).isEqualTo(8);
        assertThat(createdConfig.getMinimumSize()).isEqualTo(16);
        assertThat(createdConfig.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(13);
    }

    /**
     * Ensures the given exercise has a (managed) plagiarism detection config, creating one when absent.
     */
    private PlagiarismDetectionConfig ensurePlagiarismDetectionConfig(ProgrammingExercise exercise) {
        PlagiarismDetectionConfig config = exercise.getPlagiarismDetectionConfig();
        if (config == null) {
            config = new PlagiarismDetectionConfig();
            exercise.setPlagiarismDetectionConfig(config);
        }
        return config;
    }

    /**
     * Applies a valid build plan configuration so that the exercise passes general update validation and any rejection can
     * be attributed to the plagiarism config under test.
     */
    private void setValidBuildPlanConfiguration() throws Exception {
        var phase = new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml"));
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
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

    private void setupLocalVCRepository(LocalRepository localRepo, ProgrammingExercise exercise) throws Exception {
        String projectKey = exercise.getProjectKey();
        String templateRepositorySlug = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();

        // Seed target bare repo under LocalVC and copy contents from source
        RepositoryExportTestUtil.seedLocalVcBareFrom(localVCLocalCITestService, projectKey, templateRepositorySlug, localRepo);

        // Wire URI to template participation for this exercise
        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, templateRepositorySlug));
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);
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

    /**
     * Gives instructor1 the instructor role in {@link #course}. Membership is expressed through the user's course role
     * rather than the course's instructor group name, which no longer drives membership checks.
     */
    private void addInstructorToCourse() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(instructor, course, CourseRole.INSTRUCTOR);
    }
}
