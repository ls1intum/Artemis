package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.quiz.QuizExerciseFactory;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;
import de.tum.cit.aet.artemis.util.RequestUtilService;

@Isolated
class LtiQuizIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ltiquizsubmissiontest";

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private QuizSubmissionService quizSubmissionService;

    @BeforeEach
    void init() {
        doNothing().when(lti13Service).onNewResult(any());
    }

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        super.resetSpyBeans();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLtiServicesAreCalledUponQuizSubmission(boolean isSubmitted) throws Exception {
        QuizExercise quizExercise = createSimpleQuizExercise(ZonedDateTime.now().minusMinutes(1), 240);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        quizSubmission.submitted(isSubmitted);

        request.postWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live?submit=" + isSubmitted, quizSubmission, QuizSubmission.class, HttpStatus.OK);

        if (isSubmitted) {
            assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isOne();
        }
        else {
            assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();

        }

        verifyNoInteractions(lti13Service);

        // End the quiz right now
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDueDate(ZonedDateTime.now());
        exerciseRepository.saveAndFlush(quizExercise);

        quizSubmissionService.calculateAllResults(quizExercise.getId());

        verify(lti13Service).onNewResult(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLtiReevaluateStatistics() throws Exception {

        QuizExercise quizExercise = createQuizExercise(ZonedDateTime.now().plusHours(5));
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(2));

        var now = ZonedDateTime.now();

        // generate submissions for each student
        int numberOfParticipants = 10;
        userUtilService.addStudents(TEST_PREFIX, 2, 14);

        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, now.minusHours(3));
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // calculate statistics
        QuizExercise quizExerciseWithRecalculatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK,
                QuizExercise.class);

        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(10);
        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);

        verify(lti13Service, times(10)).onNewResult(any());

    }

    private QuizExercise createSimpleQuizExercise(ZonedDateTime releaseDate, int duration) {
        Course course = courseUtilService.createCourse();
        course.setOnlineCourse(true);
        courseRepository.save(course);

        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, releaseDate, null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(duration);
        return quizExercise;
    }

    private QuizExercise createQuizExercise(ZonedDateTime releaseDate) throws Exception {
        QuizExercise quizExercise = createSimpleQuizExercise(releaseDate, 3600);

        QuizExercise quizExerciseServer = createQuizExerciseWithFiles(quizExercise);
        assertThat(quizExerciseServer).isNotNull();
        QuizExercise quizExerciseDatabase = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExerciseServer.getId());
        assertThat(quizExerciseServer).isNotNull();
        assertThat(quizExerciseDatabase).isNotNull();

        return quizExerciseDatabase;
    }

    private QuizExercise createQuizExerciseWithFiles(QuizExercise quizExercise) throws Exception {
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/quiz-exercises");
        addFilesToBuilderAndModifyExercise(builder, quizExercise);
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(quizExercise)))
                .contentType(MediaType.MULTIPART_FORM_DATA);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().is(HttpStatus.CREATED.value())).andReturn();
        request.restoreSecurityContext();
        if (HttpStatus.valueOf(result.getResponse().getStatus()).is2xxSuccessful()) {
            assertThat(result.getResponse().getContentAsString()).isNotBlank();
            return objectMapper.readValue(result.getResponse().getContentAsString(), QuizExercise.class);
        }
        return null;
    }

    private void addFilesToBuilderAndModifyExercise(MockMultipartHttpServletRequestBuilder builder, QuizExercise quizExercise) {
        int index = 0;
        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                String backgroundFileName = "backgroundImage" + index++ + ".jpg";
                dragAndDropQuestion.setBackgroundFilePath(backgroundFileName);
                builder.file(new MockMultipartFile("files", backgroundFileName, MediaType.IMAGE_JPEG_VALUE, "backgroundImage".getBytes()));

                for (var dragItem : dragAndDropQuestion.getDragItems()) {
                    if (dragItem.getPictureFilePath() != null) {
                        String filename = "dragItemImage" + index++ + ".png";
                        dragItem.setPictureFilePath(filename);
                        builder.file(new MockMultipartFile("files", filename, MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()));
                    }
                }
            }
        }
    }
}
