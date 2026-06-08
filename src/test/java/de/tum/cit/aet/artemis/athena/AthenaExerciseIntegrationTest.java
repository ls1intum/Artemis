package de.tum.cit.aet.artemis.athena;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_RESTRICTED_MODULE_TEXT_TEST;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AthenaExerciseIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenaexerciseintegration";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    private Course course;

    private TextExercise textExercise;

    @BeforeEach
    @Override
    protected void initTestCase() {
        super.initTestCase();

        userUtilService.addUsers(TEST_PREFIX, 0, 0, 1, 1);

        course = courseUtilService.addEmptyCourse();
        textExercise = textExerciseUtilService.createSampleTextExercise(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateTextExercise_useRestrictedAthenaModule_success() throws Exception {
        textExercise.setId(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.postWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateTextExercise_useRestrictedAthenaModule_success2() throws Exception {
        textExercise.setId(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.postWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExercise_useRestrictedAthenaModule_success() throws Exception {
        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        TextExercise updatedExercise = request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise),
                TextExercise.class, HttpStatus.OK);

        // Wait for async Weaviate upsert operation to complete to prevent race conditions
        assertExerciseExistsInWeaviate(weaviateService, updatedExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExercise_useRestrictedAthenaModule_success2() throws Exception {
        textExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExercise.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExercise_afterDueDate_badRequest() throws Exception {
        textExercise.setDueDate(ZonedDateTime.now());
        textExerciseRepository.save(textExercise);

        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);

        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExamTextExercise_useAthena_badRequest() throws Exception {
        ExerciseGroup group = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise examTextExercise = TextExerciseFactory.generateTextExerciseForExam(group);
        examTextExercise.setFeedbackSuggestionModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST);

        request.postWithResponseBody("/api/text/text-exercises", examTextExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateTestExamTextExercise_useAthena_success() throws Exception {
        Exam testExam = examUtilService.addTestExamWithExerciseGroup(course, true);
        ExerciseGroup group = testExam.getExerciseGroups().getFirst();
        TextExercise testExamTextExercise = TextExerciseFactory.generateTextExerciseForExam(group);
        testExamTextExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);

        TextExercise created = request.postWithResponseBody("/api/text/text-exercises", testExamTextExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(created.getFeedbackSuggestionModule()).isEqualTo(ATHENA_MODULE_TEXT_TEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTestExamTextExercise_useAthena_success() throws Exception {
        Exam testExam = examUtilService.addTestExamWithExerciseGroup(course, true);
        ExerciseGroup group = testExam.getExerciseGroups().getFirst();
        TextExercise testExamTextExercise = textExerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(group));
        testExamTextExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);

        TextExercise updated = request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(testExamTextExercise),
                TextExercise.class, HttpStatus.OK);
        assertThat(updated.getFeedbackSuggestionModule()).isEqualTo(ATHENA_MODULE_TEXT_TEST);
    }

}
