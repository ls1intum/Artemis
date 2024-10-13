package de.tum.cit.aet.artemis.iris.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSubSettingsRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisSettingsIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irissettingsintegration";

    @Autowired
    private IrisSubSettingsRepository irisSubSettingsRepository;

    @Autowired
    private IrisSettingsRepository irisSettingsRepository;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getMissingSettingsForCourse() throws Exception {
        activateIrisGlobally();
        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings2).isNotNull().usingRecursiveComparison().ignoringFieldsOfTypes(HashSet.class, TreeSet.class).ignoringActualNullFields()
                .isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(course, false));
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison()
                .ignoringFields("id", "course", "irisChatSettings.id", "iris_lecture_ingestion_settings_id", "irisCompetencyGenerationSettings.id")
                .isEqualTo(irisSettingsService.getDefaultSettingsFor(course));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseSettings() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison()
                .ignoringFields("id", "course", "irisChatSettings.id", "irisLectureIngestionSettings.id", "irisCompetencyGenerationSettings.id").ignoringExpectedNullFields()
                .isEqualTo(loadedSettings2);
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("course")
                .isEqualTo(irisSettingsRepository.findCourseSettings(course.getId()).orElseThrow());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseSettingsAsUser() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.FORBIDDEN, IrisSettings.class);
        var loadedSettings = request.get("/api/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings).isNotNull().usingRecursiveComparison().ignoringCollectionOrderInFields("irisChatSettings.allowedVariants",
                "irisLectureIngestionSettings.allowedVariants", "irisCompetencyGenerationSettings.allowedVariants").ignoringFields("id")
                .isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(course, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings1() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        loadedSettings1.getIrisChatSettings().setEnabled(false);
        loadedSettings1.getIrisCompetencyGenerationSettings().setEnabled(false);
        loadedSettings1.getIrisLectureIngestionSettings().setEnabled(false);

        var updatedSettings = request.putWithResponseBody("/api/courses/" + course.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class, HttpStatus.OK);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings2);
        // Ids of settings should not have changed
        assertThat(updatedSettings.getId()).isEqualTo(loadedSettings1.getId());
        assertThat(updatedSettings.getIrisLectureIngestionSettings().getId()).isEqualTo(loadedSettings1.getIrisLectureIngestionSettings().getId());
        assertThat(updatedSettings.getIrisChatSettings().getId()).isEqualTo(loadedSettings1.getIrisChatSettings().getId());
        assertThat(updatedSettings.getIrisCompetencyGenerationSettings().getId()).isEqualTo(loadedSettings1.getIrisCompetencyGenerationSettings().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings2() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        var chatSubSettingsId = loadedSettings1.getIrisChatSettings().getId();
        var competencyGenerationSubSettingsId = loadedSettings1.getIrisCompetencyGenerationSettings().getId();
        var lectureIngestionSubSettingsId = loadedSettings1.getIrisLectureIngestionSettings().getId();
        loadedSettings1.setIrisLectureIngestionSettings(null);
        loadedSettings1.setIrisChatSettings(null);
        loadedSettings1.setIrisCompetencyGenerationSettings(null);

        var updatedSettings = request.putWithResponseBody("/api/courses/" + course.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class, HttpStatus.OK);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings1);
        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings2);
        // Original subsettings should not exist anymore
        assertThat(irisSubSettingsRepository.findById(lectureIngestionSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(chatSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(competencyGenerationSubSettingsId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings3() throws Exception {
        activateIrisGlobally();
        course = courseRepository.findByIdElseThrow(course.getId());

        var courseSettings = new IrisCourseSettings();
        courseSettings.setCourse(course);
        courseSettings.setIrisChatSettings(new IrisChatSubSettings());
        courseSettings.getIrisChatSettings().setEnabled(true);
        courseSettings.getIrisChatSettings().setSelectedVariant(null);

        courseSettings.setIrisCompetencyGenerationSettings(new IrisCompetencyGenerationSubSettings());
        courseSettings.getIrisCompetencyGenerationSettings().setEnabled(true);
        courseSettings.getIrisCompetencyGenerationSettings().setSelectedVariant(null);

        courseSettings.setIrisLectureIngestionSettings(new IrisLectureIngestionSubSettings());
        courseSettings.getIrisLectureIngestionSettings().setEnabled(true);

        var updatedSettings = request.putWithResponseBody("/api/courses/" + course.getId() + "/raw-iris-settings", courseSettings, IrisSettings.class, HttpStatus.OK);
        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).usingRecursiveComparison().ignoringFields("course").isEqualTo(loadedSettings1);
        assertThat(loadedSettings1).usingRecursiveComparison().ignoringFields("id", "course", "irisChatSettings.id", "irisChatSettings.template.id",
                "irisLectureIngestionSettings.id", "irisCompetencyGenerationSettings.id", "irisCompetencyGenerationSettings.template.id").isEqualTo(courseSettings);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getMissingSettingsForProgrammingExercise() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        var loadedSettings1 = request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        assertThat(loadedSettings2).isNotNull().usingRecursiveComparison().ignoringFields("id", "irisChatSettings.id").ignoringFieldsOfTypes(HashSet.class, TreeSet.class)
                .ignoringActualNullFields().isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(programmingExercise, false));
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("id", "exercise", "irisChatSettings.id")
                .isEqualTo(irisSettingsService.getDefaultSettingsFor(programmingExercise));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExerciseSettings() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var loadedSettings1 = request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("id", "exercise", "irisChatSettings.id", "irisTextExerciseChatSettings.id")
                .ignoringExpectedNullFields().isEqualTo(loadedSettings2);
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

        request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.FORBIDDEN, IrisSettings.class);
        var loadedSettings = request.get("/api/exercises/" + programmingExercise.getId() + "/iris-settings", HttpStatus.OK, IrisCombinedSettingsDTO.class);

        assertThat(loadedSettings).isNotNull().usingRecursiveComparison().ignoringFields("id")
                .ignoringCollectionOrderInFields("irisChatSettings.allowedVariants", "irisCompetencyGenerationSettings.allowedVariants")
                .isEqualTo(irisSettingsService.getCombinedIrisSettingsFor(programmingExercise, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseSettings1() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var loadedSettings1 = request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        loadedSettings1.getIrisChatSettings().setEnabled(false);

        var updatedSettings = request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class,
                HttpStatus.OK);
        var loadedSettings2 = request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings2);
        // Ids of settings should not have changed
        assertThat(updatedSettings.getId()).isEqualTo(loadedSettings1.getId());
        assertThat(updatedSettings.getIrisChatSettings().getId()).isEqualTo(loadedSettings1.getIrisChatSettings().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseSettings2() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());

        var loadedSettings1 = request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        var chatSubSettingsId = loadedSettings1.getIrisChatSettings().getId();
        loadedSettings1.setIrisChatSettings(null);

        var updatedSettings = request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class,
                HttpStatus.OK);
        var loadedSettings2 = request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

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
        exerciseSettings.setExercise(programmingExercise);
        exerciseSettings.setIrisChatSettings(new IrisChatSubSettings());
        exerciseSettings.getIrisChatSettings().setEnabled(true);
        exerciseSettings.getIrisChatSettings().setSelectedVariant(null);

        var updatedSettings = request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", exerciseSettings, IrisSettings.class,
                HttpStatus.OK);
        var loadedSettings1 = request.get("/api/exercises/" + programmingExercise.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings1);
        assertThat(loadedSettings1).usingRecursiveComparison().ignoringFields("id", "exercise", "irisChatSettings.id", "irisChatSettings.template.id").isEqualTo(exerciseSettings);
    }
}
