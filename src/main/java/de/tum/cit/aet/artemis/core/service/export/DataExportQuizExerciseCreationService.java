package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.service.DragAndDropQuizAnswerConversionService;

/**
 * A service to create the data export for quiz exercise participations.
 * This includes creating a pdf highlighting the submitted answers for drag and drop questions and
 * txt files containing the submitted answers for multiple choice and short answer questions.
 * Additionally, the results can be included in the export if the due date is over.
 * This service is also used to export the student submissions for archival.
 */
@Profile(PROFILE_CORE)
@Service
public class DataExportQuizExerciseCreationService {

    static final String TXT_FILE_EXTENSION = ".txt";

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final DragAndDropQuizAnswerConversionService dragAndDropQuizAnswerConversionService;

    private final StudentParticipationRepository studentParticipationRepository;

    public DataExportQuizExerciseCreationService(QuizSubmissionRepository quizSubmissionRepository, QuizQuestionRepository quizQuestionRepository,
            DragAndDropQuizAnswerConversionService dragAndDropQuizAnswerConversionService, StudentParticipationRepository studentParticipationRepository) {
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.dragAndDropQuizAnswerConversionService = dragAndDropQuizAnswerConversionService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Creates an export for an exercise participation of a quiz exercise.
     *
     * @param quizExercise   the quiz exercise for which the export should be created
     * @param participation  the participation for which the export should be created
     * @param outputDir      the directory in which the export should be stored
     * @param includeResults true if the results should be included in the export (if the due date is over)
     */
    public void createQuizAnswersExport(QuizExercise quizExercise, StudentParticipation participation, Path outputDir, boolean includeResults) {
        createQuizAnswersExport(quizExercise, participation, outputDir, includeResults, Optional.empty());
    }

    /**
     * Creates an export for an exercise participation of a quiz exercise.
     * Exports Drag and Drop questions as pdfs and multiple choice and short answer questions as txt files.
     *
     * @param quizExercise   the quiz exercise for which the export should be created
     * @param participation  the participation for which the export should be created
     * @param outputDir      the directory in which the export should be stored
     * @param includeResults true if the results should be included in the export (if the due date is over)
     * @param exportErrors   an optional list of errors that occurred during the export
     * @return true if the export was successful, false otherwise
     */
    private boolean createQuizAnswersExport(QuizExercise quizExercise, StudentParticipation participation, Path outputDir, boolean includeResults,
            Optional<List<String>> exportErrors) {
        Set<QuizQuestion> quizQuestions = quizQuestionRepository.findByExercise_Id(quizExercise.getId());
        boolean errorOccurred = false;
        for (var submission : participation.getSubmissions()) {
            QuizSubmission quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(submission.getId());
            List<String> multipleChoiceQuestionsSubmissions = new ArrayList<>();
            List<String> shortAnswerQuestionsSubmissions = new ArrayList<>();
            for (var question : quizQuestions) {
                var submittedAnswer = quizSubmission.getSubmittedAnswerForQuestion(question);
                // if this question wasn't answered, the submitted answer is null
                if (submittedAnswer != null) {
                    if (submittedAnswer instanceof DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
                        try {
                            dragAndDropQuizAnswerConversionService.convertDragAndDropQuizAnswerAndStoreAsPdf(dragAndDropSubmittedAnswer, outputDir, includeResults);
                        }
                        catch (IOException e) {
                            errorOccurred = true;
                            exportErrors.ifPresent(errors -> errors.add("Failed to export drag and drop answers for quiz submission " + submission.getId() + " of quiz exercise "
                                    + quizExercise.getTitle() + " with id " + quizExercise.getId()));
                        }
                    }
                    else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
                        shortAnswerQuestionsSubmissions.add(createExportForShortAnswerQuestion(shortAnswerSubmittedAnswer, includeResults));
                    }
                    else if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) {
                        multipleChoiceQuestionsSubmissions.add(createExportForMultipleChoiceAnswerQuestion(multipleChoiceSubmittedAnswer, includeResults));
                    }
                }
            }
            if (!multipleChoiceQuestionsSubmissions.isEmpty()) {
                try {
                    FileUtils.writeLines(outputDir.resolve("quiz_submission_" + submission.getId() + "_multiple_choice_questions_answers" + TXT_FILE_EXTENSION).toFile(),
                            StandardCharsets.UTF_8.name(), multipleChoiceQuestionsSubmissions);
                }
                catch (IOException e) {
                    errorOccurred = true;
                    exportErrors.ifPresent(errors -> errors.add("Failed to export multiple choice answers for quiz submission " + submission.getId() + " of quiz exercise "
                            + quizExercise.getTitle() + " with id " + quizExercise.getId()));
                }
            }
            if (!shortAnswerQuestionsSubmissions.isEmpty()) {
                try {
                    FileUtils.writeLines(outputDir.resolve("quiz_submission_" + submission.getId() + "_short_answer_questions_answers" + TXT_FILE_EXTENSION).toFile(),
                            StandardCharsets.UTF_8.name(), shortAnswerQuestionsSubmissions);
                }
                catch (IOException e) {
                    errorOccurred = true;
                    exportErrors.ifPresent(errors -> errors.add("Failed to export short answer answers for quiz submission " + submission.getId() + " of quiz exercise "
                            + quizExercise.getTitle() + " with id " + quizExercise.getId()));
                }
            }
        }
        return !errorOccurred;
    }

    /**
     * Exports the student submissions for a quiz exercise.
     *
     * @param quizExercise          the quiz exercise for which the submissions should be exported
     * @param exerciseDir           the directory in which the submissions should be stored
     * @param exportErrors          a list of errors that occurred during the export
     * @param archivalReportEntries a list of report entries to report failed/successful exports
     */
    public void exportStudentSubmissionsForArchival(QuizExercise quizExercise, Path exerciseDir, @NotNull List<String> exportErrors,
            List<ArchivalReportEntry> archivalReportEntries) {
        var participations = studentParticipationRepository.findByExerciseIdWithEagerSubmissions(quizExercise.getId());
        int participationsWithoutSubmission = 0;
        int successfulExports = 0;
        for (var participation : participations) {
            if (participation.getSubmissions().isEmpty()) {
                participationsWithoutSubmission++;
                continue;
            }
            var outputDir = exerciseDir.resolve("participation-" + participation.getId() + "-" + participation.getParticipantIdentifier());
            try {
                DataExportUtil.createDirectoryIfNotExistent(outputDir);
            }
            catch (IOException e) {
                exportErrors.add("Failed to create directory for quiz exercise participation " + participation.getId() + "of quiz exercise " + quizExercise.getTitle() + " with id "
                        + quizExercise.getId() + ". Won't export this participation.");
                continue;
            }
            boolean successful = createQuizAnswersExport(quizExercise, participation, outputDir, true, Optional.ofNullable(exportErrors));
            if (successful) {
                successfulExports++;
            }
        }
        archivalReportEntries
                .add(new ArchivalReportEntry(quizExercise, quizExercise.getSanitizedExerciseTitle(), participations.size(), successfulExports, participationsWithoutSubmission));
    }

    /**
     * Creates a txt file containing the submitted answers for a multiple choice question and information if the answer was correct or not if includeResults is true.
     *
     * @param multipleChoiceSubmittedAnswer the submitted answer to a multiple choice question that should be included in the export
     * @param includeResults                true if the results should be included in the export (if the assessment due date or result publication date is over)
     * @return the content for the txt file as a string
     */
    private String createExportForMultipleChoiceAnswerQuestion(MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer, boolean includeResults) {
        StringBuilder stringBuilder = new StringBuilder();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) multipleChoiceSubmittedAnswer.getQuizQuestion();
        if (question.isSingleChoice()) {
            stringBuilder.append("Single Choice Question: ");
        }
        else {
            stringBuilder.append("Multiple Choice Question: ");
        }
        stringBuilder.append(question.getTitle()).append("\n");
        if (includeResults) {
            stringBuilder.append("Your score: ").append(multipleChoiceSubmittedAnswer.getScoreInPoints()).append("\n");
        }
        for (var answerOption : question.getAnswerOptions()) {
            if (includeResults) {
                addExplanationToAnswerOptionWithResult(multipleChoiceSubmittedAnswer, stringBuilder, answerOption);
            }
            else {
                addExplanationToAnswerOptionWithoutResult(multipleChoiceSubmittedAnswer, stringBuilder, answerOption);
            }
            stringBuilder.append(answerOption.getText()).append("\n");
        }
        return stringBuilder.toString();
    }

    /**
     * Adds an explanation to the answer option if no result should be included in the export.
     * <p>
     * The explanation contains information if the answer option was selected or not or if it is invalid.
     *
     * @param multipleChoiceSubmittedAnswer the submitted answer to a multiple choice question that should be included in the export
     * @param stringBuilder                 the string builder user to create the txt file content
     * @param answerOption                  the answer option for which the explanation should be added
     */
    private void addExplanationToAnswerOptionWithoutResult(MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer, StringBuilder stringBuilder, AnswerOption answerOption) {
        if (answerOption.isInvalid()) {
            stringBuilder.append("Invalid answer option: ");
        }
        else if (multipleChoiceSubmittedAnswer.getSelectedOptions().contains(answerOption)) {
            stringBuilder.append("Selected answer: ");
        }
        else {
            stringBuilder.append("Not selected answer: ");
        }
    }

    /**
     * Adds an explanation to the answer option if a result should be included in the export.
     * <p>
     * The explanation contains information if the answer option was selected or not or it is invalid and if the answer option is correct or not.
     *
     * @param multipleChoiceSubmittedAnswer the submitted answer to a multiple choice question that should be included in the export
     * @param stringBuilder                 the string builder user to create the txt file content
     * @param answerOption                  the answer option for which the explanation should be added
     */
    private void addExplanationToAnswerOptionWithResult(MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer, StringBuilder stringBuilder, AnswerOption answerOption) {
        if (answerOption.isInvalid()) {
            stringBuilder.append("Invalid answer option: ");
        }
        else if (answerOption.isIsCorrect() && multipleChoiceSubmittedAnswer.getSelectedOptions().contains(answerOption)) {
            stringBuilder.append("Correct and selected answer: ");
        }
        else if (answerOption.isIsCorrect() && !multipleChoiceSubmittedAnswer.getSelectedOptions().contains(answerOption)) {
            stringBuilder.append("Correct but NOT selected answer: ");
        }
        else if (!answerOption.isIsCorrect() && multipleChoiceSubmittedAnswer.getSelectedOptions().contains(answerOption)) {
            stringBuilder.append("Incorrect but selected answer: ");
        }
        else if (!answerOption.isIsCorrect() && !multipleChoiceSubmittedAnswer.getSelectedOptions().contains(answerOption)) {
            stringBuilder.append("Incorrect and NOT selected answer: ");
        }
    }

    /**
     * Creates a txt file containing the submitted answers for a short answer question and information if the answer was correct or not if includeResults is true.
     *
     * @param shortAnswerSubmittedAnswer the submitted answer to a short answer question that should be included in the export
     * @param includeResults             true if the results should be included in the export (if the assessment due date or result publication date is over)
     * @return the content for the txt file as a string
     */
    private String createExportForShortAnswerQuestion(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer, boolean includeResults) {
        StringBuilder stringBuilder = new StringBuilder();
        ShortAnswerQuestion question = (ShortAnswerQuestion) shortAnswerSubmittedAnswer.getQuizQuestion();
        stringBuilder.append("Short Answer Question: ").append(question.getTitle()).append("\n");
        if (includeResults) {
            stringBuilder.append("Your score: ").append(shortAnswerSubmittedAnswer.getScoreInPoints()).append("\n");
        }
        return replaceSpotWithSubmittedAnswer(shortAnswerSubmittedAnswer, stringBuilder, includeResults);
    }

    /**
     * Replaces the spots (the gaps that indicate where an answer should be entered) in the text of a short answer question with the submitted answers.
     *
     * @param shortAnswerSubmittedAnswer the submitted answer to a short answer question that should be included in the export
     * @param submittedAnswer            the string builder user to create the txt file content
     * @param includeResults             true if the results should be included in the export (if the assessment due date or result publication date is over)
     * @return the string containing the question text with the answers of the user
     */
    private String replaceSpotWithSubmittedAnswer(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer, StringBuilder submittedAnswer, boolean includeResults) {
        var spotToSubmittedTextMap = buildMapFromSpotsToSubmittedAnswers(shortAnswerSubmittedAnswer);
        submittedAnswer.append("Your answer: ").append("\n");
        submittedAnswer.append(shortAnswerSubmittedAnswer.getQuizQuestion().getText());
        for (Map.Entry<String, ShortAnswerSubmittedText> entry : spotToSubmittedTextMap.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey());
            Matcher matcher = pattern.matcher(submittedAnswer);
            while (matcher.find()) {
                StringBuilder replacement = new StringBuilder();
                matcher = addSubmittedAnswerWithResult(submittedAnswer, includeResults, entry.getValue(), pattern, matcher, replacement);
            }
        }
        return submittedAnswer.toString();
    }

    /**
     * Adds the submitted answer to the string builder if the answer is correct or incorrect.
     *
     * @param submittedAnswer the string builder user to create the txt file content
     * @param includeResults  true if the results should be included in the export (if the assessment due date or result publication date is over)
     * @param submittedText   the question text of a short answer question that should be included in the export
     * @param pattern         the pattern used to find the spot in the question text
     * @param matcher         the matcher used to find the spot in the question text
     * @param replacement     the string builder used to create the replacement (the submitted answer text) for the spot
     * @return the matcher used to find the next spot in the question text
     */
    private Matcher addSubmittedAnswerWithResult(StringBuilder submittedAnswer, boolean includeResults, ShortAnswerSubmittedText submittedText, Pattern pattern, Matcher matcher,
            StringBuilder replacement) {
        int start = matcher.start();
        int end = matcher.end();
        replacement.append(submittedText.getText());
        if (submittedText.isIsCorrect() != null && submittedText.isIsCorrect() && includeResults) {
            replacement.append(" (Correct)");
        }
        else if (submittedText.isIsCorrect() != null && !submittedText.isIsCorrect() && includeResults) {
            replacement.append(" (Incorrect)");
        }
        submittedAnswer.replace(start, end, replacement.toString());
        matcher = pattern.matcher(submittedAnswer);
        return matcher;
    }

    /**
     * Builds a map from the spots (the gaps that indicate where an answer should be entered) in the text of a short answer question to the submitted answers.
     *
     * @param shortAnswerSubmittedAnswer the submitted answer to a short answer question that should be included in the export
     * @return a map from the spots (represented as the string in text) to the submitted answers
     */
    private Map<String, ShortAnswerSubmittedText> buildMapFromSpotsToSubmittedAnswers(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        Map<String, ShortAnswerSubmittedText> spotsToSubmittedAnswers = new HashMap<>();
        for (var submittedText : shortAnswerSubmittedAnswer.getSubmittedTexts()) {
            int spotNr = submittedText.getSpot().getSpotNr();
            spotsToSubmittedAnswers.put("\\[-spot\\s*" + spotNr + "\\]", submittedText);

        }
        return spotsToSubmittedAnswers;
    }
}
