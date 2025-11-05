package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseFromEditorDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Service responsible for initializing the database with specific testdata related to quiz exercises for use in integration tests.
 */
public class AbstractQuizExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    protected QuizExerciseService quizExerciseService;

    @Autowired
    protected QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    protected QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    protected ExamUtilService examUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    protected Course createEmptyCourse() {
        final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);
        final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);
        return quizExerciseUtilService.createAndSaveCourse(1L, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, Set.of());
    }

    protected QuizExercise importQuizExerciseWithFiles(QuizExercise quizExercise, Long id, List<MockMultipartFile> files, HttpStatus expectedStatus) throws Exception {
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/quiz/quiz-exercises/import/" + id);
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(quizExercise)))
                .contentType(MediaType.MULTIPART_FORM_DATA);
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        MvcResult result = request.performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
        request.restoreSecurityContext();
        if (expectedStatus == HttpStatus.CREATED) {
            return objectMapper.readValue(result.getResponse().getContentAsString(), QuizExercise.class);
        }
        return null;
    }

    protected QuizExercise createQuizOnServer(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(releaseDate, dueDate, quizMode);
        quizExercise.setDuration(3600);

        QuizExercise quizExerciseServer = createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, true);
        QuizExercise quizExerciseDatabase = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExerciseServer.getId());
        assertThat(quizExerciseServer).isNotNull();
        assertThat(quizExerciseDatabase).isNotNull();

        checkQuizExercises(quizExercise, quizExerciseServer);
        checkQuizExercises(quizExercise, quizExerciseDatabase);

        for (int i = 0; i < quizExercise.getQuizQuestions().size(); i++) {
            QuizQuestion question = quizExercise.getQuizQuestions().get(i);
            QuizQuestion questionServer = quizExerciseServer.getQuizQuestions().get(i);
            QuizQuestion questionDatabase = quizExerciseDatabase.getQuizQuestions().get(i);

            assertThat(question.getId()).as("Question IDs are correct").isNull();
            assertThat(questionDatabase.getId()).as("Question IDs are correct").isEqualTo(questionServer.getId());

            assertThat(question.getExercise().getId()).as("Exercise IDs are correct").isNull();
            assertThat(questionDatabase.getExercise().getId()).as("Exercise IDs are correct").isEqualTo(quizExerciseDatabase.getId());

            assertThat(question.getTitle()).as("Question titles are correct").isEqualTo(questionDatabase.getTitle());
            assertThat(question.getTitle()).as("Question titles are correct").isEqualTo(questionServer.getTitle());

            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionDatabase.getPoints());
            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionServer.getPoints());
        }
        return quizExerciseDatabase;
    }

    protected QuizExercise createQuizOnServerForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.createAndSaveActiveExerciseGroup(createEmptyCourse(), true);
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        quizExercise.setDuration(3600);

        QuizExercise quizExerciseServer = createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, true);
        QuizExercise quizExerciseDatabase = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExerciseServer.getId());
        assertThat(quizExerciseServer).isNotNull();
        assertThat(quizExerciseDatabase).isNotNull();

        checkQuizExercises(quizExercise, quizExerciseServer);
        checkQuizExercises(quizExercise, quizExerciseDatabase);

        for (int i = 0; i < quizExercise.getQuizQuestions().size(); i++) {
            QuizQuestion question = quizExercise.getQuizQuestions().get(i);
            QuizQuestion questionServer = quizExerciseServer.getQuizQuestions().get(i);
            QuizQuestion questionDatabase = quizExerciseDatabase.getQuizQuestions().get(i);

            assertThat(question.getId()).as("Question IDs are correct").isNull();
            assertThat(questionDatabase.getId()).as("Question IDs are correct").isEqualTo(questionServer.getId());

            assertThat(question.getExercise().getId()).as("Exercise IDs are correct").isNull();
            assertThat(questionDatabase.getExercise().getId()).as("Exercise IDs are correct").isEqualTo(quizExerciseDatabase.getId());

            assertThat(question.getTitle()).as("Question titles are correct").isEqualTo(questionDatabase.getTitle());
            assertThat(question.getTitle()).as("Question titles are correct").isEqualTo(questionServer.getTitle());

            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionDatabase.getPoints());
            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionServer.getPoints());
        }

        return quizExerciseDatabase;
    }

    /**
     * Sends a create request for a quiz exercise. It automatically adds the files to the request and modifies the exercise to be sent.
     *
     * @param quizExercise       the quiz exercise to be sent
     * @param expectedStatus     the expected status of the request
     * @param addBackgroundImage whether to add a background image to the quiz exercise
     * @return the created quiz exercise or null if the request failed
     */
    protected QuizExercise createQuizExerciseWithFiles(QuizExercise quizExercise, HttpStatus expectedStatus, boolean addBackgroundImage) throws Exception {
        String url;
        if (quizExercise.isExamExercise()) {
            url = "/api/quiz/exercise-groups/" + quizExercise.getExerciseGroup().getId() + "/quiz-exercises";
        }
        else {
            url = "/api/quiz/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quiz-exercises";
        }
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, url);
        addFilesToBuilderAndModifyExercise(builder, quizExercise, addBackgroundImage);
        QuizExerciseCreateDTO dto = QuizExerciseCreateDTO.of(quizExercise);
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto))).contentType(MediaType.MULTIPART_FORM_DATA);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
        request.restoreSecurityContext();
        if (HttpStatus.valueOf(result.getResponse().getStatus()).is2xxSuccessful()) {
            assertThat(result.getResponse().getContentAsString()).isNotBlank();
            return objectMapper.readValue(result.getResponse().getContentAsString(), QuizExercise.class);
        }
        return null;
    }

    /**
     * Check that the general information of two exercises is equal.
     */
    private void checkQuizExercises(QuizExercise quizExercise, QuizExercise quizExercise2) {
        assertThat(quizExercise.getQuizQuestions()).as("Same amount of questions saved").hasSameSizeAs(quizExercise2.getQuizQuestions());
        assertThat(quizExercise.getTitle()).as("Title saved correctly").isEqualTo(quizExercise2.getTitle());
        assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Number of attempts saved correctly").isEqualTo(quizExercise2.getAllowedNumberOfAttempts());
        assertThat(quizExercise.getMaxPoints()).as("Max score saved correctly").isEqualTo(quizExercise2.getMaxPoints());
        assertThat(quizExercise.getDuration()).as("Duration saved correctly").isEqualTo(quizExercise2.getDuration());
        assertThat(quizExercise.getType()).as("Type saved correctly").isEqualTo(quizExercise2.getType());
    }

    private void addFilesToBuilderAndModifyExercise(MockMultipartHttpServletRequestBuilder builder, QuizExercise quizExercise, boolean addBackgroundImage) {
        int index = 0;
        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (addBackgroundImage) {
                    String backgroundFileName = "backgroundImage" + index++ + ".jpg";
                    dragAndDropQuestion.setBackgroundFilePath(backgroundFileName);
                    builder.file(new MockMultipartFile("files", backgroundFileName, MediaType.IMAGE_JPEG_VALUE, "backgroundImage".getBytes()));
                }
                else {
                    dragAndDropQuestion.setBackgroundFilePath(null);
                }
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

    protected QuizExercise updateQuizExerciseWithFiles(QuizExercise quizExercise, List<String> fileNames, HttpStatus expectedStatus) throws Exception {
        return updateQuizExerciseWithFiles(quizExercise, fileNames, expectedStatus, null);
    }

    /**
     * Sends an update request for the given quiz exercise.
     *
     * @param quizExercise   the quiz exercise to update. Expects to contain changed filenames if appliccable
     * @param fileNames      the filenames of changed or new files
     * @param expectedStatus the expected status of the request
     * @return the updated quiz exercise or null if the request failed
     */
    protected QuizExercise updateQuizExerciseWithFiles(QuizExercise quizExercise, List<String> fileNames, HttpStatus expectedStatus, MultiValueMap<String, String> params)
            throws Exception {
        QuizExerciseFromEditorDTO dto = new QuizExerciseFromEditorDTO(quizExercise.getTitle(), quizExercise.getChannelName(), quizExercise.getCategories(),
                quizExercise.getCompetencyLinks(), quizExercise.getDifficulty(), quizExercise.getDuration(), quizExercise.isRandomizeQuestionOrder(), quizExercise.getQuizMode(),
                quizExercise.getQuizBatches(), quizExercise.getReleaseDate(), quizExercise.getStartDate(), quizExercise.getDueDate(), quizExercise.getIncludedInOverallScore(),
                quizExercise.getQuizQuestions());

        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PATCH, "/api/quiz/quiz-exercises/" + quizExercise.getId());
        if (params != null) {
            builder.params(params);
        }
        if (fileNames != null) {
            for (String fileName : fileNames) {
                builder.file(new MockMultipartFile("files", fileName, MediaType.IMAGE_PNG_VALUE, "test".getBytes()));
            }
        }
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto))).contentType(MediaType.MULTIPART_FORM_DATA);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
        request.restoreSecurityContext();
        if (HttpStatus.valueOf(result.getResponse().getStatus()).is2xxSuccessful()) {
            return objectMapper.readValue(result.getResponse().getContentAsString(), QuizExercise.class);
        }
        return null;
    }

    /**
     * Sends an update + re-evaluate request for the given quiz exercise.
     *
     * @param quizExercise   the quiz exercise to save
     * @param id             the id of the quiz exercise
     * @param files          the files to be uploaded
     * @param expectedStatus the expected status for HTTP request
     * @return updated QuizExercise or null if the request failed
     */
    protected void reevalQuizExerciseWithFiles(QuizExercise quizExercise, Long id, List<MockMultipartFile> files, HttpStatus expectedStatus) throws Exception {
        QuizExerciseReEvaluateDTO dto = QuizExerciseReEvaluateDTO.of(quizExercise);
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/quiz/quiz-exercises/" + id + "/re-evaluate");
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto))).contentType(MediaType.MULTIPART_FORM_DATA);
        for (MockMultipartFile file : files) {
            builder.file(file);
        }

        MvcResult result = request.performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
        request.restoreSecurityContext();
    }
}
