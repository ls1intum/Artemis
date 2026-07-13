package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class QuizExerciseExportIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "quizexerciseexport";

    @Autowired
    private QuizExerciseWithSubmissionsExportService exportService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @TempDir
    private Path exportDirectory;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @Test
    void shouldPreserveQuestionComponentsAndStudentRawAnswersWhenExportingQuiz() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2), QuizMode.INDIVIDUAL);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        dragAndDropQuestion.getDragItems().forEach(dragItem -> dragItem.setPictureFilePath(null));
        ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        shortAnswerQuestion.setText("This [-spot0] a [-spot2] answer text");
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission submission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, ZonedDateTime.now().minusHours(1));
        // DnD submissions are exported as image-based PDFs and are covered separately. This characterization focuses on persistence-owned definition data and raw text exports.
        submission.getSubmittedAnswers().removeIf(DragAndDropSubmittedAnswer.class::isInstance);
        submission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
        participationUtilService.addSubmission(quizExercise, submission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(submission), true);

        List<String> exportErrors = new ArrayList<>();
        List<ArchivalReportEntry> reportEntries = new ArrayList<>();
        exportService.exportExerciseWithSubmissions(quizExercise, exportDirectory, exportErrors, reportEntries);

        assertThat(exportErrors).isEmpty();
        assertThat(reportEntries).hasSize(1);
        Path exerciseDetails;
        try (var files = Files.list(exportDirectory)) {
            exerciseDetails = files.filter(path -> path.getFileName().toString().startsWith("Exercise-Details-") && path.getFileName().toString().endsWith(".json")).findFirst()
                    .orElseThrow();
        }
        JsonNode exerciseJson = objectMapper.readTree(exerciseDetails.toFile());
        JsonNode questions = exerciseJson.path("quizQuestions");
        assertThat(questions).hasSize(3);
        for (JsonNode answerOption : findQuestionByType(questions, "multiple-choice").path("answerOptions")) {
            assertThat(answerOption.path("id").isIntegralNumber()).as("answer option ID should be an integral number").isTrue();
        }
        assertThat(findQuestionByType(questions, "drag-and-drop").path("dragItems")).isNotEmpty();
        assertThat(findQuestionByType(questions, "drag-and-drop").path("dropLocations")).isNotEmpty();
        assertThat(findQuestionByType(questions, "drag-and-drop").path("correctMappings")).isNotEmpty();
        assertThat(findQuestionByType(questions, "short-answer").path("spots")).isNotEmpty();
        assertThat(findQuestionByType(questions, "short-answer").path("solutions")).isNotEmpty();
        assertThat(findQuestionByType(questions, "short-answer").path("correctMappings")).isNotEmpty();

        Path multipleChoiceAnswers = findExportedAnswerFile("multiple_choice_questions_answers");
        Path shortAnswerAnswers = findExportedAnswerFile("short_answer_questions_answers");
        assertThat(Files.readString(multipleChoiceAnswers)).contains("Multiple Choice Question:", "selected answer");
        assertThat(Files.readString(shortAnswerAnswers)).contains("Short Answer Question:", "IS", "LONG");

        entityManager.clear();
        QuizExercise reloadedQuiz = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        assertThat(reloadedQuiz.getCourseViaExerciseGroupOrCourseMember()).isNotNull();
        assertThat(((ShortAnswerQuestion) reloadedQuiz.getQuizQuestions().get(2)).getSolutions()).isNotEmpty();
    }

    private Path findExportedAnswerFile(String namePart) throws Exception {
        try (var files = Files.walk(exportDirectory)) {
            return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().contains(namePart)).findFirst().orElseThrow();
        }
    }

    private static JsonNode findQuestionByType(JsonNode questions, String type) {
        for (JsonNode question : questions) {
            if (type.equals(question.path("type").asText())) {
                return question;
            }
        }
        throw new AssertionError("Missing quiz question of type " + type);
    }
}
