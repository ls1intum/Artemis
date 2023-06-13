package de.tum.in.www1.artemis.iris.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettings;
import de.tum.in.www1.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSubSettingsRepository;
import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;

public class IrisSettingsIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irissettingsintegration";

    @Autowired
    private IrisSubSettingsRepository irisSubSettingsRepository;

    @Autowired
    private IrisSettingsRepository irisSettingsRepository;

    private Course course;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getMissingSettingsForCourse() throws Exception {
        activateIrisGlobally();
        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("id", "irisChatSettings.id", "irisHestiaSettings.id")
                .isEqualTo(irisSettingsService.addDefaultIrisSettingsTo(course).getIrisSettings());
        assertThat(loadedSettings2).isNotNull().usingRecursiveComparison().ignoringFields("id", "irisChatSettings.id", "irisHestiaSettings.id")
                .isEqualTo(irisSettingsService.getCombinedIrisSettings(course, false));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseSettings() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().ignoringFields("id", "irisChatSettings.id", "irisHestiaSettings.id").isEqualTo(loadedSettings2);
        assertThat(loadedSettings1).isNotNull().usingRecursiveComparison().isEqualTo(irisSettingsRepository.findById(course.getIrisSettings().getId()).orElseThrow());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings1() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        loadedSettings1.getIrisChatSettings().setEnabled(false);
        loadedSettings1.getIrisHestiaSettings().setEnabled(false);

        var updatedSettings = request.putWithResponseBody("/api/courses/" + course.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class, HttpStatus.OK);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings2);
        // Ids of settings should not have changed
        assertThat(updatedSettings.getId()).isEqualTo(loadedSettings1.getId());
        assertThat(updatedSettings.getIrisChatSettings().getId()).isEqualTo(loadedSettings1.getIrisChatSettings().getId());
        assertThat(updatedSettings.getIrisHestiaSettings().getId()).isEqualTo(loadedSettings1.getIrisHestiaSettings().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings2() throws Exception {
        activateIrisGlobally();
        activateIrisFor(course);
        course = courseRepository.findByIdElseThrow(course.getId());

        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        var chatSubSettingsId = loadedSettings1.getIrisChatSettings().getId();
        var hestiaSubSettingsId = loadedSettings1.getIrisHestiaSettings().getId();
        loadedSettings1.setIrisChatSettings(null);
        loadedSettings1.setIrisHestiaSettings(null);

        var updatedSettings = request.putWithResponseBody("/api/courses/" + course.getId() + "/raw-iris-settings", loadedSettings1, IrisSettings.class, HttpStatus.OK);
        var loadedSettings2 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().isEqualTo(loadedSettings1);
        assertThat(updatedSettings).isNotNull().usingRecursiveComparison().isEqualTo(loadedSettings2);
        // Original subsettings should not exist anymore
        assertThat(irisSubSettingsRepository.findById(chatSubSettingsId)).isEmpty();
        assertThat(irisSubSettingsRepository.findById(hestiaSubSettingsId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseSettings3() throws Exception {
        activateIrisGlobally();
        course = courseRepository.findByIdElseThrow(course.getId());

        course.setIrisSettings(new IrisSettings());
        course.getIrisSettings().setIrisChatSettings(new IrisSubSettings());
        course.getIrisSettings().getIrisChatSettings().setEnabled(true);
        course.getIrisSettings().getIrisChatSettings().setTemplate(createDummyTemplate());
        course.getIrisSettings().getIrisChatSettings().setPreferredModel(IrisModel.GPT35);
        course.getIrisSettings().setIrisHestiaSettings(new IrisSubSettings());
        course.getIrisSettings().getIrisHestiaSettings().setEnabled(true);
        course.getIrisSettings().getIrisHestiaSettings().setTemplate(createDummyTemplate());
        course.getIrisSettings().getIrisHestiaSettings().setPreferredModel(IrisModel.GPT35);

        var updatedSettings = request.putWithResponseBody("/api/courses/" + course.getId() + "/raw-iris-settings", course.getIrisSettings(), IrisSettings.class, HttpStatus.OK);
        var loadedSettings1 = request.get("/api/courses/" + course.getId() + "/raw-iris-settings", HttpStatus.OK, IrisSettings.class);

        assertThat(updatedSettings).isNotNull().isEqualTo(loadedSettings1);
        assertThat(loadedSettings1).usingRecursiveComparison()
                .ignoringFields("id", "irisChatSettings.id", "irisHestiaSettings.id", "irisChatSettings.template.id", "irisHestiaSettings.template.id")
                .isEqualTo(course.getIrisSettings());
    }
}
