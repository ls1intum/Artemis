package de.tum.cit.aet.artemis.lti;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;

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

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;

@Isolated
class LtiQuizIntegrationTest extends AbstractLtiIntegrationTest {

    private static final String TEST_PREFIX = "ltiquizsubmissiontest";

    @Autowired
    private ResultTestRepository resultRepository;

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
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        QuizExercise quizExercise = createSimpleQuizExercise(ZonedDateTime.now().minusMinutes(1), 240);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }

        quizSubmission.submitted(isSubmitted);

        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/live?submit=" + isSubmitted, quizSubmission, QuizSubmission.class,
                HttpStatus.OK);

        if (isSubmitted) {
            assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isOne();
        }
        else {
            assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();

        }

        verifyNoInteractions(lti13Service);

        // End the quiz right now
        quizExercise = quizExerciseTestRepository.findOneWithQuestionsAndCategoriesAndBatches(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDueDate(ZonedDateTime.now());
        exerciseRepository.saveAndFlush(quizExercise);

        quizSubmissionService.calculateAllResults(quizExercise.getId());

        await().atMost(2, SECONDS).untilAsserted(() -> verify(lti13Service).onNewResult(any()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLtiServicesAreCalledUponQuizReevaluation() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        QuizExercise quizExercise = createSimpleQuizExercise(ZonedDateTime.now().minusMinutes(1), 240);
        quizExercise = quizExerciseService.save(quizExercise);
        StudentParticipation participation = null;
        ZonedDateTime completionDate = ZonedDateTime.now().minusSeconds(1);

        for (int i = 0; i < 2; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();
            for (var question : quizExercise.getQuizQuestions()) {
                quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
            }
            quizSubmission.submitted(true);
            quizSubmission.setSubmissionDate(completionDate.plusNanos(i));
            if (participation == null) {
                participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");
                participation = (StudentParticipation) quizSubmission.getParticipation();
            }
            else {
                participationUtilService.addSubmission(participation, quizSubmission);
            }
            participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, completionDate.plusNanos(i), quizSubmission, true, true, 100);
        }

        verifyNoInteractions(lti13Service);
        assertThat(participation.getId()).isNotNull();
        long participationId = participation.getId();
        quizExercise = quizExerciseService.reEvaluate(QuizExerciseReEvaluateDTO.of(quizExercise), quizExercise, List.of());

        List<Result> reEvaluatedResults = resultRepository.findByExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        assertThat(reEvaluatedResults).hasSize(2).allSatisfy(result -> assertThat(result.getScore()).isEqualTo(11.1));
        await().atMost(2, SECONDS)
                .untilAsserted(() -> verify(lti13Service, times(1)).onNewResult(argThat(actualParticipation -> actualParticipation.getId().equals(participationId))));

        clearInvocations(lti13Service);
        quizExerciseService.reEvaluate(QuizExerciseReEvaluateDTO.of(quizExercise), quizExercise, List.of());
        verifyNoInteractions(lti13Service);
    }

    private QuizExercise createSimpleQuizExercise(ZonedDateTime releaseDate, int duration) {
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
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
        QuizExercise quizExerciseDatabase = quizExerciseTestRepository.findOneWithQuestionsAndCategoriesAndBatches(quizExerciseServer.getId());
        assertThat(quizExerciseServer).isNotNull();
        assertThat(quizExerciseDatabase).isNotNull();

        return quizExerciseDatabase;
    }

    private QuizExercise createQuizExerciseWithFiles(QuizExercise quizExercise) throws Exception {
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/quiz/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quiz-exercises");
        addFilesToBuilderAndModifyExercise(builder, quizExercise);
        QuizExerciseCreateDTO dto = QuizExerciseCreateDTO.of(quizExercise);
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto))).contentType(MediaType.MULTIPART_FORM_DATA);
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
