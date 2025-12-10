package de.tum.cit.aet.artemis.iris.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisFaqIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisGlobalSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisLectureChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisProgrammingExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisTextExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisTutorSuggestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSubSettingsRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class IrisSettingsIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irissettingsintegration";

    @Autowired
    private IrisSubSettingsRepository irisSubSettingsRepository;

    @Autowired
    private IrisSettingsRepository irisSettingsRepository;

    @Autowired
    private AeolusTemplateService aeolusTemplateService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private IrisSettingsApi irisSettingsApi;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private TextExercise textExercise;

    // @formatter:off
    private static Stream<Arguments> getCourseSettingsCategoriesSource() {
        return Stream.of(
                Arguments.of(List.of("COURSE"), List.of(List.of("category1")), false),
                Arguments.of(List.of("COURSE", "EXERCISE"), List.of(List.of("category1"), List.of("category1")), true),
                Arguments.of(List.of("EXERCISE", "COURSE"), List.of(List.of("category1"), List.of("category1")), true),
                Arguments.of(List.of("EXERCISE"), List.of(List.of("category1")), false),
                Arguments.of(List.of("EXERCISE", "COURSE", "EXERCISE"), List.of(List.of("category1"), List.of("category1"), List.of()), false),
                Arguments.of(List.of("COURSE", "EXERCISE", "COURSE"), List.of(List.of("category1"), List.of("category1"), List.of()), false),
                Arguments.of(List.of("EXERCISE", "COURSE", "EXERCISE"), List.of(List.of("category1", "category2"), List.of("category1"), List.of("category2")), false),
                Arguments.of(List.of("EXERCISE", "COURSE", "EXERCISE"), List.of(List.of("category1", "category2"), List.of("category1"), List.of("category1")), true),
                Arguments.of(List.of("EXERCISE", "COURSE", "EXERCISE"), List.of(List.of("category1"), List.of("category2"), List.of("category2")), true),
                Arguments.of(List.of("COURSE", "EXERCISE", "COURSE"), List.of(List.of("category1", "category2"), List.of("category1"), List.of("category2")), false),
                Arguments.of(List.of("COURSE", "EXERCISE", "COURSE"), List.of(List.of("category1", "category2"), List.of("category1"), List.of("category1")), true)
        );
    }
    // @formatter:on

    @BeforeEach
    void initTestCase() throws JsonProcessingException {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var projectKey1 = programmingExercise.getProjectKey();
        programmingExercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-tests.git");
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(aeolusTemplateService.getDefaultWindfileFor(programmingExercise)));
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        var templateRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "exercise");
        var templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey1 + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        var solutionRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "solution");
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        // Text Exercise
        ZonedDateTime pastReleaseDate = ZonedDateTime.now().minusDays(5);
        ZonedDateTime pastDueDate = ZonedDateTime.now().minusDays(3);
        ZonedDateTime pastAssessmentDueDate = ZonedDateTime.now().minusDays(2);
        textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastReleaseDate, pastDueDate, pastAssessmentDueDate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getMissingSettingsForCourse() throws Exception {
        activateIrisGlobally();
        var loadedSettings1 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/iris/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings2).isNotNull().usingRecursiveComparison().ignoringFieldsOfTypes(HashSet.class, TreeSet.class).ignoringActualNullFields()
                .isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(course, false));
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison()
                .ignoringFields("id", "course", "irisProgrammingExerciseChatSettings.id", "iris_lecture_ingestion_settings_id", "irisCompetencyGenerationSettings.id")
                .isEqualTo(irisSettingsService.getDefaultSettingsFor(course));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseSettings() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/iris/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison()
                .ignoringFields("id", "course", "irisProgrammingExerciseChatSettings.id", "irisLectureIngestionSettings.id", "irisCompetencyGenerationSettings.id")
                .ignoringExpectedNullFields().isEqualTo(loadedSettings2);
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("course")
                .isEqualTo(irisSettingsRepository.findCourseSettings(course.getId()).orElseThrow());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseSettingsAsUser() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.FORBIDDEN, IrisSettings.class);
        var loadedSettings = request.get("/api/iris/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);
        assertThat(loadedSettings).isNotNull().usingRecursiveComparison()
                .ignoringCollectionOrderInFields("irisProgrammingExerciseChatSettings.allowedVariants", "irisProgrammingExerciseChatSettings.disabledProactiveEvents",
                        "irisLectureIngestionSettings.allowedVariants", "irisCompetencyGenerationSettings.allowedVariants")
                .ignoringFields("id").isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(course, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings1() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        loadedSettings1.getIrisProgrammingExerciseChatSettings().setEnabled(false);
        loadedSettings1.getIrisTextExerciseChatSettings().setEnabled(false);
        loadedSettings1.getIrisCourseChatSettings().setEnabled(false);
        loadedSettings1.getIrisCompetencyGenerationSettings().setEnabled(false);
        loadedSettings1.getIrisLectureIngestionSettings().setEnabled(false);
        loadedSettings1.getIrisLectureChatSettings().setEnabled(false);
        loadedSettings1.getIrisTutorSuggestionSettings().setEnabled(false);

        var updatedSettings = request.putWithResponseBody("/api/iris/courses/" + course.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class, HttpStatus.OK);
        var loadedSettings2 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings2);
        // Ids of settings should not have changed
        assertThat(updatedSettings.getId()).isEqualTo(loadedSettings1.getId());
        assertThat(updatedSettings.getIrisProgrammingExerciseChatSettings().getId()).isEqualTo(loadedSettings1.getIrisProgrammingExerciseChatSettings().getId());
        assertThat(updatedSettings.getIrisTextExerciseChatSettings().getId()).isEqualTo(loadedSettings1.getIrisTextExerciseChatSettings().getId());
        assertThat(updatedSettings.getIrisCourseChatSettings().getId()).isEqualTo(loadedSettings1.getIrisCourseChatSettings().getId());
        assertThat(updatedSettings.getIrisCompetencyGenerationSettings().getId()).isEqualTo(loadedSettings1.getIrisCompetencyGenerationSettings().getId());
        assertThat(updatedSettings.getIrisLectureIngestionSettings().getId()).isEqualTo(loadedSettings1.getIrisLectureIngestionSettings().getId());
        assertThat(updatedSettings.getIrisLectureChatSettings().getId()).isEqualTo(loadedSettings1.getIrisLectureChatSettings().getId());
        assertThat(updatedSettings.getIrisTutorSuggestionSettings().getId()).isEqualTo(loadedSettings1.getIrisTutorSuggestionSettings().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings2() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        var chatSubSettingsId = loadedSettings1.getIrisProgrammingExerciseChatSettings().getId();
        var textExerciseChatSubSettingsId = loadedSettings1.getIrisTextExerciseChatSettings().getId();
        var courseChatSubSettingsId = loadedSettings1.getIrisCourseChatSettings().getId();
        var competencyGenerationSubSettingsId = loadedSettings1.getIrisCompetencyGenerationSettings().getId();
        var lectureIngestionSubSettingsId = loadedSettings1.getIrisLectureIngestionSettings().getId();
        var tutorSuggestionSubSettingsId = loadedSettings1.getIrisTutorSuggestionSettings().getId();
        var lectureChatSubSettingsId = loadedSettings1.getIrisLectureChatSettings().getId();

        loadedSettings1.setIrisProgrammingExerciseChatSettings(null);
        loadedSettings1.setIrisTextExerciseChatSettings(null);
        loadedSettings1.setIrisCourseChatSettings(null);
        loadedSettings1.setIrisCompetencyGenerationSettings(null);
        loadedSettings1.setIrisLectureIngestionSettings(null);
        loadedSettings1.setIrisTutorSuggestionSettings(null);
        loadedSettings1.setIrisLectureChatSettings(null);

        var updatedSettings = request.putWithResponseBody("/api/iris/courses/" + course.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class, HttpStatus.OK);
        var loadedSettings2 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings1);
        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings2);
        // Original subsettings should not exist anymore
        assertThat(irisSubSettingsRepository.findById(chatSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(textExerciseChatSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(courseChatSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(competencyGenerationSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(lectureIngestionSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(tutorSuggestionSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(lectureChatSubSettingsId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings3() throws Exception {
        activateIrisGlobally();
        course = courseRepository.findByIdElseThrow(course.getId());

        var courseSettings = new IrisCourseSettings();
        courseSettings.setCourseId(course.getId());
        courseSettings.setIrisProgrammingExerciseChatSettings(new IrisProgrammingExerciseChatSubSettings());
        courseSettings.getIrisProgrammingExerciseChatSettings().setEnabled(true);
        courseSettings.getIrisProgrammingExerciseChatSettings().setSelectedVariant(null);

        courseSettings.setIrisTextExerciseChatSettings(new IrisTextExerciseChatSubSettings());
        courseSettings.getIrisTextExerciseChatSettings().setEnabled(true);
        courseSettings.getIrisTextExerciseChatSettings().setSelectedVariant(null);

        courseSettings.setIrisCourseChatSettings(new IrisCourseChatSubSettings());
        courseSettings.getIrisCourseChatSettings().setEnabled(true);
        courseSettings.getIrisCourseChatSettings().setSelectedVariant(null);

        courseSettings.setIrisCompetencyGenerationSettings(new IrisCompetencyGenerationSubSettings());
        courseSettings.getIrisCompetencyGenerationSettings().setEnabled(true);
        courseSettings.getIrisCompetencyGenerationSettings().setSelectedVariant(null);

        courseSettings.setIrisLectureIngestionSettings(new IrisLectureIngestionSubSettings());
        courseSettings.getIrisLectureIngestionSettings().setEnabled(true);
        courseSettings.getIrisLectureIngestionSettings().setSelectedVariant(null);

        courseSettings.setIrisLectureChatSettings(new IrisLectureChatSubSettings());
        courseSettings.getIrisLectureChatSettings().setEnabled(true);
        courseSettings.getIrisLectureChatSettings().setSelectedVariant(null);

        courseSettings.setIrisTutorSuggestionSettings(new IrisTutorSuggestionSubSettings());
        courseSettings.getIrisTutorSuggestionSettings().setEnabled(true);
        courseSettings.getIrisTutorSuggestionSettings().setSelectedVariant(null);

        var updatedSettings = request.putWithResponseBody("/api/iris/courses/" + course.getId() + "/raw-iris-settings", courseSettings, IrisSettings.class, HttpStatus.OK);
        var loadedSettings1 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings1);
        assertThat(loadedSettings1).usingRecursiveComparison()
                .ignoringFields("id", "course", "irisProgrammingExerciseChatSettings.id", "irisTextExerciseChatSettings.id", "irisLectureIngestionSettings.id",
                        "irisCompetencyGenerationSettings.id", "irisCourseChatSettings.id", "irisLectureChatSettings.id", "irisTutorSuggestionSettings.id")
                .isEqualTo(courseSettings);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings4() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        loadedSettings1.getIrisProgrammingExerciseChatSettings().setDisabledProactiveEvents(new TreeSet<>(Set.of("PROGRESS_STALLED")));
        loadedSettings1.getIrisLectureChatSettings().setCustomInstructions("Test lecture chat instructions");

        var updatedSettings = request.putWithResponseBody("/api/iris/courses/" + course.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class, HttpStatus.OK);
        var loadedSettings2 = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        // Proactive events should have been updated
        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings2);
        assertThat(updatedSettings.getIrisProgrammingExerciseChatSettings().getDisabledProactiveEvents()).containsExactly("PROGRESS_STALLED");
        assertThat(updatedSettings.getIrisLectureChatSettings().getCustomInstructions()).isEqualTo("Test lecture chat instructions");
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings2);
    }

    /**
     * This test check if exercises get correctly enabled and disabled based on the categories in the course settings.
     *
     * @param operations      List of operations to perform on the settings. Possible values are "COURSE" and "EXERCISE".
     * @param categories      List of categories to set for the course and exercise settings.
     * @param exerciseEnabled Expected value of the exercise enabled flag.
     * @throws Exception If something request fails.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("getCourseSettingsCategoriesSource")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettingsCategories(List<String> operations, List<List<String>> categories, boolean exerciseEnabled) throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        disableIrisFor(programmingExercise);
        disableIrisFor(textExercise);
        course = courseRepository.findByIdElseThrow(course.getId());

        for (int i = 0; i < operations.size(); i++) {
            String operation = operations.get(i);
            SortedSet<String> category = new TreeSet<>(categories.get(i));
            if (operation.equals("COURSE")) {
                var loadedSettings = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
                loadedSettings.getIrisProgrammingExerciseChatSettings().setEnabledForCategories(category);
                loadedSettings.getIrisTextExerciseChatSettings().setEnabledForCategories(category);

                request.putWithResponseBody("/api/iris/courses/" + course.getId() + "/raw-iris-settings", loadedSettings, IrisSettings.class, HttpStatus.OK);
            }
            else if (operation.equals("EXERCISE")) {
                programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();
                programmingExercise.setCategories(category.stream().map(cat -> "{\"color\":\"#6ae8ac\",\"category\":\"" + cat + "\"}").collect(Collectors.toSet()));
                request.putWithResponseBody("/api/programming/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

                textExercise = (TextExercise) exerciseRepository.findByIdElseThrow(textExercise.getId());
                textExercise.setCategories(category.stream().map(cat -> "{\"color\":\"#6ae8ac\",\"category\":\"" + cat + "\"}").collect(Collectors.toSet()));
                request.putWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.OK);
            }
        }

        // Load programming exercise Iris settings
        var loadedSettings1 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        assertThat(loadedSettings1.getIrisProgrammingExerciseChatSettings().isEnabled()).isEqualTo(exerciseEnabled);

        // Load text exercise Iris settings
        var loadedSettings2 = request.get("/api/iris/exercises/" + textExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        assertThat(loadedSettings2.getIrisTextExerciseChatSettings().isEnabled()).isEqualTo(exerciseEnabled);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getMissingSettingsForProgrammingExercise() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        var loadedSettings1 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        assertThat(loadedSettings2).isNotNull().usingRecursiveComparison().ignoringFields("id", "irisProgrammingExerciseChatSettings.id")
                .ignoringFieldsOfTypes(HashSet.class, TreeSet.class).ignoringActualNullFields()
                .isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(programmingExercise, false));
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("id", "exercise", "irisProgrammingExerciseChatSettings.id")
                .isEqualTo(irisSettingsService.getDefaultSettingsFor(programmingExercise));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExerciseSettings() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var loadedSettings1 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison()
                .ignoringFields("id", "exercise", "irisProgrammingExerciseChatSettings.id", "irisTextExerciseChatSettings.id").ignoringExpectedNullFields()
                .isEqualTo(loadedSettings2);
        assertThat(loadedSettings1.getIrisCompetencyGenerationSettings()).isNull();
        assertThat(loadedSettings1.getIrisLectureIngestionSettings()).isNull();
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("exercise")
                .isEqualTo(irisSettingsRepository.findExerciseSettings(programmingExercise.getId()).orElseThrow());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getProgrammingExerciseSettingsAsUser() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.FORBIDDEN, IrisSettings.class);
        var loadedSettings = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings).isNotNull().usingRecursiveComparison().ignoringFields("id")
                .ignoringCollectionOrderInFields("irisProgrammingExerciseChatSettings.allowedVariants", "irisCompetencyGenerationSettings.allowedVariants")
                .isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(programmingExercise, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseSettings1() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var loadedSettings1 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        loadedSettings1.getIrisProgrammingExerciseChatSettings().setEnabled(false);

        var updatedSettings = request.putWithResponseBody("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class,
                HttpStatus.OK);
        var loadedSettings2 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings2);
        // Ids of settings should not have changed
        assertThat(updatedSettings.getId()).isEqualTo(loadedSettings1.getId());
        assertThat(updatedSettings.getIrisProgrammingExerciseChatSettings().getId()).isEqualTo(loadedSettings1.getIrisProgrammingExerciseChatSettings().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseSettings2() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var loadedSettings1 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        var chatSubSettingsId = loadedSettings1.getIrisProgrammingExerciseChatSettings().getId();
        loadedSettings1.setIrisProgrammingExerciseChatSettings(null);

        var updatedSettings = request.putWithResponseBody("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class,
                HttpStatus.OK);
        var loadedSettings2 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().ignoringFields("exercise").isEqualTo(loadedSettings1);
        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().ignoringFields("exercise").isEqualTo(loadedSettings2);
        // Original subsettings should not exist anymore
        assertThat(irisSubSettingsRepository.findById(chatSubSettingsId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseSettings3() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var exerciseSettings = new IrisExerciseSettings();
        exerciseSettings.setExerciseId(programmingExercise.getId());
        exerciseSettings.setIrisProgrammingExerciseChatSettings(new IrisProgrammingExerciseChatSubSettings());
        exerciseSettings.getIrisProgrammingExerciseChatSettings().setEnabled(true);
        exerciseSettings.getIrisProgrammingExerciseChatSettings().setSelectedVariant(null);

        var updatedSettings = request.putWithResponseBody("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", exerciseSettings, IrisSettings.class,
                HttpStatus.OK);
        var loadedSettings1 = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings1);
        assertThat(loadedSettings1).usingRecursiveComparison()
                .ignoringFields("id", "exercise", "irisProgrammingExerciseChatSettings.id", "irisProgrammingExerciseChatSettings.template.id").isEqualTo(exerciseSettings);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseSettings4() throws Exception {
        activateIrisGlobally();
        course = courseRepository.findByIdElseThrow(course.getId());

        var courseSettings = new IrisCourseSettings();
        courseSettings.setCourseId(course.getId());
        courseSettings.setIrisProgrammingExerciseChatSettings(new IrisProgrammingExerciseChatSubSettings());
        courseSettings.getIrisProgrammingExerciseChatSettings().setEnabled(true);
        courseSettings.getIrisProgrammingExerciseChatSettings().setSelectedVariant(null);
        courseSettings.getIrisProgrammingExerciseChatSettings().setDisabledProactiveEvents(new TreeSet<>(Set.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase())));

        request.putWithResponseBody("/api/iris/courses/" + course.getId() + "/raw-iris-settings", courseSettings, IrisSettings.class, HttpStatus.OK);
        request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var exerciseSettings = new IrisExerciseSettings();
        exerciseSettings.setExerciseId(programmingExercise.getId());
        exerciseSettings.setIrisProgrammingExerciseChatSettings(new IrisProgrammingExerciseChatSubSettings());
        exerciseSettings.getIrisProgrammingExerciseChatSettings().setEnabled(true);
        exerciseSettings.getIrisProgrammingExerciseChatSettings().setSelectedVariant(null);
        exerciseSettings.getIrisProgrammingExerciseChatSettings().setDisabledProactiveEvents(new TreeSet<>(Set.of(IrisEventType.BUILD_FAILED.name().toLowerCase())));

        request.putWithResponseBody("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", exerciseSettings, IrisSettings.class, HttpStatus.OK);
        var loadedExerciseSettings = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);
        // Combined settings should include the union of the disabled course events and disabled exercise events
        assertThat(loadedExerciseSettings.irisProgrammingExerciseChatSettings().disabledProactiveEvents()).isNotNull()
                .isEqualTo(new TreeSet<>(Set.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase(), IrisEventType.BUILD_FAILED.name().toLowerCase())));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void verifyDefaultCourseSettingsState() {
        Course newCourse = courseUtilService.createCourse();

        IrisCourseSettings courseSettings = irisSettingsService.getDefaultSettingsFor(newCourse);

        assertThat(courseSettings.getIrisProgrammingExerciseChatSettings().isEnabled()).isTrue();
        assertThat(courseSettings.getIrisTextExerciseChatSettings().isEnabled()).isTrue();
        assertThat(courseSettings.getIrisCourseChatSettings().isEnabled()).isTrue();
        assertThat(courseSettings.getIrisLectureIngestionSettings().isEnabled()).isTrue();
        assertThat(courseSettings.getIrisLectureIngestionSettings().getAutoIngestOnLectureAttachmentUpload()).isTrue();
        assertThat(courseSettings.getIrisLectureChatSettings().isEnabled()).isTrue();
        assertThat(courseSettings.getIrisFaqIngestionSettings().isEnabled()).isTrue();
        assertThat(courseSettings.getIrisFaqIngestionSettings().getAutoIngestOnFaqCreation()).isTrue();
        assertThat(courseSettings.getIrisCompetencyGenerationSettings().isEnabled()).isTrue();
        assertThat(courseSettings.getIrisTutorSuggestionSettings().isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void verifyDefaultExerciseSettingsState() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1); // Ensure instructor exists for course creation
        Course courseWithExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise newProgrammingExercise = ExerciseUtilService.getFirstExerciseWithType(courseWithExercise, ProgrammingExercise.class);

        IrisExerciseSettings exerciseSettings = irisSettingsService.getDefaultSettingsFor(newProgrammingExercise);

        assertThat(exerciseSettings.getIrisProgrammingExerciseChatSettings().isEnabled()).isTrue();
        // Assert that TextExerciseChatSettings are now instantiated and enabled by default for Programming Exercises as well
        assertThat(exerciseSettings.getIrisTextExerciseChatSettings()).isNotNull();
        assertThat(exerciseSettings.getIrisTextExerciseChatSettings().isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void verifyDefaultFaqIngestionSettingsState() {
        Course courseWithFaq = courseUtilService.createCourse();
        IrisFaqIngestionSubSettings faqSettings = irisSettingsService.getDefaultSettingsFor(courseWithFaq).getIrisFaqIngestionSettings();

        assertThat(faqSettings.isEnabled()).isTrue();
        assertThat(faqSettings.getAutoIngestOnFaqCreation()).isTrue();
    }

    /**
     * Verifies the AND-combination logic of global and course chat enablement.
     * Iris must be enabled both globally and for the specific course for the
     * repository method to return {@code true}.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void isCourseChatEnabled_GlobalEnabled_CourseEnabled() {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        boolean enabled = irisSettingsApi.isCourseChatEnabled(course.getId());
        assertThat(enabled).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void isCourseChatEnabled_GlobalEnabled_CourseDisabled() {
        activateIrisGlobally();

        boolean enabled = irisSettingsApi.isCourseChatEnabled(course.getId());
        assertThat(enabled).isTrue();

        disableCourseChatFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        enabled = irisSettingsApi.isCourseChatEnabled(course.getId());
        assertThat(enabled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void isCourseChatEnabled_GlobalDisabled_CourseEnabled() {
        disableIrisGlobally();
        activateIrisFor(course);

        boolean enabled = irisSettingsApi.isCourseChatEnabled(course.getId());
        assertThat(enabled).isFalse();
    }

    /**
     * Verifies the enablement logic for programming‑exercise chat:
     * it is {@code true} only when the global, course, and exercise flags
     * are all (explicitly or implicitly) enabled.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void isProgrammingExerciseChatEnabled_GlobalEnabled_CourseEnabled_ExerciseEnabled() {
        activateIrisGlobally();
        activateIrisFor(course);

        // Create a programming exercise belonging to the course
        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        activateIrisFor(exercise);
        exercise = programmingExerciseRepository.findByIdElseThrow(exercise.getId());

        boolean enabled = irisSettingsApi.isProgrammingExerciseChatEnabled(exercise.getId());
        assertThat(enabled).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void isProgrammingExerciseChatEnabled_GlobalEnabled_CourseDisabled() {
        activateIrisGlobally();
        disableProgrammingExerciseChatFor(course);

        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        boolean enabled = irisSettingsApi.isProgrammingExerciseChatEnabled(exercise.getId());
        assertThat(enabled).isFalse();

        activateIrisFor(exercise);
        exercise = programmingExerciseRepository.findByIdElseThrow(exercise.getId());

        enabled = irisSettingsApi.isProgrammingExerciseChatEnabled(exercise.getId());
        assertThat(enabled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void isProgrammingExerciseChatEnabled_GlobalEnabled_ExerciseDisabled() {
        activateIrisGlobally();
        activateIrisFor(course);

        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        disableIrisFor(exercise);
        exercise = programmingExerciseRepository.findByIdElseThrow(exercise.getId());

        boolean enabled = irisSettingsApi.isProgrammingExerciseChatEnabled(exercise.getId());
        assertThat(enabled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void isProgrammingExerciseChatEnabled_GlobalDisabled_ExerciseEnabled() {
        disableIrisGlobally();

        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        activateIrisFor(exercise);
        exercise = programmingExerciseRepository.findByIdElseThrow(exercise.getId());

        boolean enabled = irisSettingsApi.isProgrammingExerciseChatEnabled(exercise.getId());
        assertThat(enabled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSettingsWithCustomInstructions() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        // 1. Test custom instructions for course settings
        var courseSettings = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        String programmingExerciseChatInstructions = "Programming chat instructions";
        String textExerciseChatInstructions = "Text exercise chat instructions";
        String courseChatSpecificInstructions = "Course-specific chat instructions";
        String lectureChatInstructions = "Lecture chat instructions";

        // Update chat settings with custom instructions
        courseSettings.getIrisProgrammingExerciseChatSettings().setCustomInstructions(programmingExerciseChatInstructions);
        courseSettings.getIrisTextExerciseChatSettings().setCustomInstructions(textExerciseChatInstructions);
        courseSettings.getIrisCourseChatSettings().setCustomInstructions(courseChatSpecificInstructions);
        courseSettings.getIrisLectureChatSettings().setCustomInstructions(lectureChatInstructions);

        var updatedCourseSettings = request.putWithResponseBody("/api/iris/courses/" + course.getId() + "/raw-iris-settings", courseSettings, IrisSettings.class, HttpStatus.OK);
        var loadedCourseSettings = request.get("/api/iris/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        // Verify that custom instructions were saved and can be retrieved
        assertThat(loadedCourseSettings.getIrisProgrammingExerciseChatSettings().getCustomInstructions()).isEqualTo(programmingExerciseChatInstructions);
        assertThat(loadedCourseSettings.getIrisTextExerciseChatSettings().getCustomInstructions()).isEqualTo(textExerciseChatInstructions);
        assertThat(loadedCourseSettings.getIrisCourseChatSettings().getCustomInstructions()).isEqualTo(courseChatSpecificInstructions);
        assertThat(loadedCourseSettings.getIrisLectureChatSettings().getCustomInstructions()).isEqualTo(lectureChatInstructions);

        // 2. Test custom instructions for programming exercise settings
        activateIrisFor(programmingExercise);
        var exerciseSettings = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        programmingExerciseChatInstructions = "Updated programming chat instructions";
        exerciseSettings.getIrisProgrammingExerciseChatSettings().setCustomInstructions(programmingExerciseChatInstructions);

        var updatedExerciseSettings = request.putWithResponseBody("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", exerciseSettings, IrisSettings.class,
                HttpStatus.OK);
        var loadedExerciseSettings = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        // Verify that custom instructions were saved and can be retrieved
        assertThat(loadedExerciseSettings.getIrisProgrammingExerciseChatSettings().getCustomInstructions()).isEqualTo(programmingExerciseChatInstructions);

        // 3. Test that customInstructions appear correctly in combined settings
        var combinedSettings = request.get("/api/iris/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        // The exercise-specific setting should override the course-wide setting
        assertThat(combinedSettings.irisProgrammingExerciseChatSettings().customInstructions()).isEqualTo(programmingExerciseChatInstructions);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGlobalSettingsDatabaseRetrieval() {
        activateIrisGlobally();
        var globalSettings = irisSettingsRepository.findGlobalSettingsElseThrow();
        assertThat(globalSettings).isNotNull();

        var fields = IrisGlobalSettings.class.getDeclaredFields();
        for (var field : fields) {
            field.setAccessible(true);
            // Only check fields that are a subclass of IrisSubSettings
            if (!IrisSubSettings.class.isAssignableFrom(field.getType())) {
                continue;
            }
            // Subsettings should not be null
            try {
                var value = field.get(globalSettings);
                assertThat(value).as("Subsettings field '%s' should not be null", field.getName()).isNotNull();
            }
            catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access subsettings field: " + field.getName(), e);
            }
        }
    }
}
