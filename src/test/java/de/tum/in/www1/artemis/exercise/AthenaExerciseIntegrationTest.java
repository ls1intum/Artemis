package de.tum.in.www1.artemis.exercise;

import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.course.CourseTestService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class AthenaExerciseIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenaexerciseintegration";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseTestService courseTestService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    private Course course;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    protected void initTestCase() {
        super.initTestCase();

        userUtilService.addUsers(TEST_PREFIX, 0, 0, 1, 1);

        course = courseUtilService.addEmptyCourse();
        textExercise = textExerciseUtilService.createSampleTextExercise(course);
        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        course.addExercises(programmingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateTextExercise_useRestrictedAthenaModule_success() throws Exception {
        course.setRestrictedAthenaModulesAccess(true);
        courseRepository.save(course);

        textExercise.setId(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateTextExercise_useRestrictedAthenaModule_badRequest() throws Exception {
        textExercise.setId(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExercise_useRestrictedAthenaModule_success() throws Exception {
        course.setRestrictedAthenaModulesAccess(true);
        courseRepository.save(course);

        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExercise_useRestrictedAthenaModule_badRequest() throws Exception {
        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExercise_afterDueDate_badRequest() throws Exception {
        textExercise.setDueDate(ZonedDateTime.now());
        textExerciseRepository.save(textExercise);

        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExamTextExercise_useAthena_badRequest() throws Exception {
        ExerciseGroup group = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise examTextExercise = TextExerciseFactory.generateTextExerciseForExam(group);
        examTextExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.postWithResponseBody("/api/text-exercises", examTextExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateCourse_revokeRestrictedAthenaModuleAccess_badRequest() throws Exception {
        course.setRestrictedAthenaModulesAccess(true);
        courseRepository.save(course);

        course.setRestrictedAthenaModulesAccess(false);

        request.performMvcRequest(courseTestService.buildUpdateCourse(course.getId(), course)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateCourse_revokeRestrictedAthenaModuleAccess_success() throws Exception {
        course.setRestrictedAthenaModulesAccess(true);
        courseRepository.save(course);

        // Set allowed modules for the default exercises
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);

        // Create two new exercises
        TextExercise textExerciseRestrictedModule = textExerciseUtilService.createSampleTextExercise(course);
        ProgrammingExercise programmingExerciseRestrictedModule = programmingExerciseUtilService.createSampleProgrammingExercise();
        course.addExercises(programmingExerciseRestrictedModule);
        // Set restricted modules for two new exercises
        textExerciseRestrictedModule.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);
        programmingExerciseRestrictedModule.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_PROGRAMMING_TEST);

        // Save all exercise changes
        textExerciseRepository.saveAll(List.of(textExercise, textExerciseRestrictedModule));
        programmingExerciseRepository.saveAll(List.of(programmingExercise, programmingExerciseRestrictedModule));

        // Revoke access to restricted Athena modules for the course
        course.setRestrictedAthenaModulesAccess(false);
        MvcResult result = request.performMvcRequest(courseTestService.buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        assertThat(updatedCourse.getRestrictedAthenaModulesAccess()).as("restricted Athena modules access was correctly updated for the course")
                .isEqualTo(course.getRestrictedAthenaModulesAccess());

        ProgrammingExercise updatedProgrammingExercise = request.get("/api/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, ProgrammingExercise.class);
        ProgrammingExercise updatedProgrammingExerciseRestrictedModule = request.get("/api/programming-exercises/" + programmingExerciseRestrictedModule.getId(), HttpStatus.OK,
                ProgrammingExercise.class);
        TextExercise updatedTextExercise = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);
        TextExercise updatedTextExerciseRestrictedModule = request.get("/api/text-exercises/" + textExerciseRestrictedModule.getId(), HttpStatus.OK, TextExercise.class);

        // Check that the default exercises still have their module set
        assertThat(updatedProgrammingExercise.getFeedbackSuggestionModule()).as("Athena module for the programming exercise was unchanged")
                .isEqualTo(programmingExercise.getFeedbackSuggestionModule());
        assertThat(updatedTextExercise.getFeedbackSuggestionModule()).as("Athena module for the text exercise was unchanged").isEqualTo(textExercise.getFeedbackSuggestionModule());

        // Check that the two additional exercises do not have a restricted module set
        assertThat(updatedProgrammingExerciseRestrictedModule.getFeedbackSuggestionModule())
                .as("access to restricted Athena module for the programming exercise was revoked successfully").isNull();
        assertThat(updatedTextExerciseRestrictedModule.getFeedbackSuggestionModule()).as("access to restricted Athena module for the text exercise was revoked successfully")
                .isNull();
    }
}
