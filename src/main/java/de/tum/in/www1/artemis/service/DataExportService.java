package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service Implementation for managing the data export in accordance with Art. 15 GDPR.
 */
@Service
public class DataExportService {

    private static final String ZIP_FILE_EXTENSION = ".zip";

    private static final String CSV_FILE_EXTENSION = ".csv";

    private static final String PDF_FILE_EXTENSION = ".pdf";

    private static final String TXT_FILE_EXTENSION = ".txt";

    private final Logger log = LoggerFactory.getLogger(DataExportService.class);

    @Value("${artemis.data-export-path}")
    private Path dataExportPath;

    @Value("${artemis.repo-download-clone-path}")
    private Path repoClonePath;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ZipFileService zipFileService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final DataExportRepository dataExportRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final DragAndDropQuizAnswerConversionService dragAndDropQuizAnswerConversionService;

    private final ApollonConversionService apollonConversionService;

    private Path workingDirectory;

    private final FileService fileService;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ReactionRepository reactionRepository;

    public DataExportService(CourseRepository courseRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService, ZipFileService zipFileService,
            ProgrammingExerciseExportService programmingExerciseExportService, DataExportRepository dataExportRepository, QuizQuestionRepository quizQuestionRepository,
            QuizSubmissionRepository quizSubmissionRepository, ExerciseRepository exerciseRepository, DragAndDropQuizAnswerConversionService dragAndDropQuizAnswerConversionService,
            ApollonConversionService apollonConversionService, FileService fileService, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            ReactionRepository reactionRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.zipFileService = zipFileService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.dataExportRepository = dataExportRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.dragAndDropQuizAnswerConversionService = dragAndDropQuizAnswerConversionService;
        this.apollonConversionService = apollonConversionService;
        this.fileService = fileService;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.reactionRepository = reactionRepository;
    }

    /**
     * Requests a data export for the given user.
     * This will create a new DataExport object in the database and start the creation of the data export.
     *
     * @param user the user for which to create the data export
     * @return the created DataExport object
     **/
    public DataExport requestDataExport(User user) throws IOException {
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        dataExport.setUser(user);
        dataExport.setRequestDate(ZonedDateTime.now());
        if (!Files.exists(dataExportPath)) {
            Files.createDirectories(dataExportPath);
        }
        dataExportRepository.save(dataExport);
        workingDirectory = Files.createTempDirectory(dataExportPath, "data-export-working-dir");
        dataExport.setDataExportState(DataExportState.IN_CREATION);
        dataExportRepository.save(dataExport);
        var dataExportPath = createDataExport(user);
        dataExport.setFilePath(dataExportPath.toString());
        // sending the email will be part of a follow-up, for now this just implies export finished
        dataExport.setCreationDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;

    }

    /**
     * Download the data export for the given user id and data export id.
     *
     * @param userId       the id of the user for which to download the data export
     * @param dataExportId the id of the data export to download
     * @return the file path where the data export is stored
     */
    public String downloadDataExport(long userId, long dataExportId) {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        var user = userRepository.findByIdElseThrow(userId);
        // check data export belongs to specified user
        authorizationCheckService.isOwnerOfDataExportElseThrow(dataExport, user);
        // check data export belongs to currently logged-in user
        authorizationCheckService.currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);

        // the first condition checks that the export has been created and the second that it has not yet been deleted (we allow multiple downloads as long as the export is not
        // deleted
        if (dataExport.getDataExportState() != DataExportState.EMAIL_SENT && dataExport.getDataExportState() != DataExportState.DOWNLOADED) {
            throw new AccessForbiddenException("Data export is not ready for download yet or has already been deleted");
        }
        dataExport.setDownloadDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport = dataExportRepository.save(dataExport);
        return dataExport.getFilePath();

    }

    /**
     * Creates the data export for the given user.
     * Retrieves all courses and exercises the user has participated in from the database.
     *
     * @param user the user for which to create the data export
     * @return the path to the created data export
     **/

    private Path createDataExport(User user) throws IOException {
        // retrieve all posts, answer posts, reactions of the user and filter them by course later to avoid additional database calls
        var posts = postRepository.findPostsByAuthorId(user.getId());
        var answerPosts = answerPostRepository.findAnswerPostsByAuthorId(user.getId());
        var reactions = reactionRepository.findReactionsByUserId(user.getId());
        var courses = courseRepository.getAllCoursesUserIsMemberOf(authorizationCheckService.isAdmin(user), user.getGroups());

        for (var course : courses) {
            Path courseDir = Files.createDirectory(workingDirectory.resolve("course_" + course.getShortName()));
            Set<Exercise> exercises = exerciseRepository.getAllExercisesUserParticipatedInWithEagerParticipationsSubmissionsResultsFeedbacksByCourseIdAndUserId(course.getId(),
                    user.getId());
            Set<ProgrammingExercise> programmingExercises = exercises.stream().filter(exercise -> exercise instanceof ProgrammingExercise)
                    .map(exercise -> (ProgrammingExercise) exercise).collect(Collectors.toSet());
            Set<TextExercise> textExercises = exercises.stream().filter(exercise -> exercise instanceof TextExercise).map(exercise -> (TextExercise) exercise)
                    .collect(Collectors.toSet());
            Set<FileUploadExercise> fileUploadExercises = exercises.stream().filter(exercise -> exercise instanceof FileUploadExercise)
                    .map(exercise -> (FileUploadExercise) exercise).collect(Collectors.toSet());
            Set<ModelingExercise> modelingExercises = exercises.stream().filter(exercise -> exercise instanceof ModelingExercise).map(exercise -> (ModelingExercise) exercise)
                    .collect(Collectors.toSet());
            Set<QuizExercise> quizExercises = exercises.stream().filter(exercise -> exercise instanceof QuizExercise).map(exercise -> (QuizExercise) exercise)
                    .collect(Collectors.toSet());
            createExportForProgrammingExercises(programmingExercises, courseDir);
            createExportForModelingExercises(modelingExercises, courseDir);
            createExportForTextExercises(textExercises, courseDir);
            createExportForFileUploadExercises(fileUploadExercises, courseDir);
            createExportForQuizExercises(quizExercises, courseDir);
            createCommunicationExport(posts, answerPosts, reactions, course.getId(), courseDir);
        }
        addGeneralUserInformation(user);
        return createDataExportZipFile(user.getLogin());

    }

    private void createExportForQuizExercises(Set<QuizExercise> quizExercises, Path courseDir) throws IOException {
        for (var quizExercise : quizExercises) {
            createNonProgrammingExerciseExport(quizExercise, courseDir);
        }
    }

    private void createExportForProgrammingExercises(Set<ProgrammingExercise> programmingExercises, Path courseDir) throws IOException {
        for (var programmingExercise : programmingExercises) {
            createProgrammingExerciseExport(programmingExercise, courseDir);
        }
    }

    private void createExportForModelingExercises(Set<ModelingExercise> modelingExercises, Path courseDir) throws IOException {
        for (var modelingExercise : modelingExercises) {
            createNonProgrammingExerciseExport(modelingExercise, courseDir);
        }
    }

    private void createExportForTextExercises(Set<TextExercise> textExercises, Path courseDir) throws IOException {
        for (var textExercise : textExercises) {
            createNonProgrammingExerciseExport(textExercise, courseDir);
        }
    }

    private void createExportForFileUploadExercises(Set<FileUploadExercise> fileUploadExercises, Path courseWorkingDir) throws IOException {
        for (var fileUploadExercise : fileUploadExercises) {
            createNonProgrammingExerciseExport(fileUploadExercise, courseWorkingDir);
        }
    }

    private void addGeneralUserInformation(User user) throws IOException {
        String[] headers = new String[] { "login", "name", "email", "registration number" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("general_user_information" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(user.getLogin(), user.getName(), user.getEmail(), user.getRegistrationNumber());
            printer.flush();

        }
    }

    private void createProgrammingExerciseExport(ProgrammingExercise programmingExercise, Path courseDir) throws IOException {
        Path exerciseDir = courseDir.resolve(programmingExercise.getSanitizedExerciseTitle());
        if (!Files.exists(exerciseDir)) {
            Files.createDirectory(exerciseDir);
        }
        createSubmissionsResultsExport(programmingExercise, exerciseDir);
        RepositoryExportOptionsDTO repositoryExportOptions = new RepositoryExportOptionsDTO();
        repositoryExportOptions.setExportAllParticipants(false);
        repositoryExportOptions.setAnonymizeStudentCommits(false);
        repositoryExportOptions.setFilterLateSubmissions(false);
        repositoryExportOptions.setCombineStudentCommits(false);
        repositoryExportOptions.setFilterLateSubmissionsIndividualDueDate(false);
        repositoryExportOptions.setExcludePracticeSubmissions(false);
        repositoryExportOptions.setHideStudentNameInZippedFolder(false);
        repositoryExportOptions.setNormalizeCodeStyle(true);
        var listOfProgrammingExerciseParticipations = programmingExercise.getStudentParticipations().stream()
                .filter(studentParticipation -> studentParticipation instanceof ProgrammingExerciseStudentParticipation)
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).toList();
        List<String> exportRepoErrors = new ArrayList<>();
        var tempRepoWorkingDir = Files.createTempDirectory(repoClonePath, "repo");
        programmingExerciseExportService.exportStudentRepositories(programmingExercise, listOfProgrammingExerciseParticipations, repositoryExportOptions, tempRepoWorkingDir,
                exerciseDir, exportRepoErrors);
        // we use this directory only to clone the repository and don't do this in our current directory because the current directory is part of the final data export
        // --> we can delete it after use
        fileService.scheduleForDirectoryDeletion(tempRepoWorkingDir, 5);

    }

    private void createSubmissionsResultsExport(Exercise exercise, Path exerciseDir) throws IOException {
        for (var participation : exercise.getStudentParticipations()) {
            // createParticipationCsvFile(participation, exerciseDir);
            for (var submission : participation.getSubmissions()) {
                createSubmissionCsvFile(submission, exerciseDir);
                if (submission instanceof FileUploadSubmission) {
                    copyFileUploadSubmissionFile(FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId()), exerciseDir);
                }
                else if (submission instanceof TextSubmission textSubmission) {
                    storeTextSubmissionContent(textSubmission, exerciseDir);
                }
                else if (submission instanceof ModelingSubmission modelingSubmission) {
                    storeModelingSubmissionContent(modelingSubmission, exerciseDir);
                }
                else if (submission instanceof QuizSubmission) {
                    createQuizAnswersExport((QuizExercise) exercise, participation, exerciseDir);
                }

                createResultsTxtFile(submission, exerciseDir);
            }
        }
    }

    private void createQuizAnswersExport(QuizExercise quizExercise, StudentParticipation participation, Path outputDir) throws IOException {
        Set<QuizQuestion> quizQuestions = quizQuestionRepository.getQuizQuestionsByExerciseId(quizExercise.getId());
        QuizSubmission quizSubmission;

        for (var submission : participation.getSubmissions()) {
            quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(submission.getId());
            List<String> multipleChoiceQuestionsSubmissions = new ArrayList<>();
            List<String> shortAnswerQuestionsSubmissions = new ArrayList<>();
            for (var question : quizQuestions) {
                var submittedAnswer = quizSubmission.getSubmittedAnswerForQuestion(question);
                // if this question wasn't answered, the submitted answer is null
                if (submittedAnswer != null) {
                    if (submittedAnswer instanceof DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
                        dragAndDropQuizAnswerConversionService.convertDragAndDropQuizAnswerAndStoreAsPdf(dragAndDropSubmittedAnswer, outputDir);
                    }
                    else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
                        shortAnswerQuestionsSubmissions.add(createExportForShortAnswerQuestion(shortAnswerSubmittedAnswer));
                    }
                    else if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) {
                        multipleChoiceQuestionsSubmissions.add(createExportForMultipleChoiceAnswerQuestion(multipleChoiceSubmittedAnswer));
                    }
                }
            }
            if (!multipleChoiceQuestionsSubmissions.isEmpty()) {
                Files.write(outputDir.resolve("quiz_submission_" + submission.getId() + "_multiple_choice_questions_answers.txt"), multipleChoiceQuestionsSubmissions);
            }
            if (!shortAnswerQuestionsSubmissions.isEmpty()) {
                Files.write(outputDir.resolve("quiz_submission_" + submission.getId() + "_short_answer_questions_answers.txt"), shortAnswerQuestionsSubmissions);
            }
        }

    }

    private String createExportForMultipleChoiceAnswerQuestion(MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) {
        StringBuilder stringBuilder = new StringBuilder();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) multipleChoiceSubmittedAnswer.getQuizQuestion();
        if (question.isSingleChoice()) {
            stringBuilder.append("Single Choice Question: ");
        }
        else {
            stringBuilder.append("Multiple Choice Question: ");
        }
        stringBuilder.append(question.getTitle()).append("\n");
        stringBuilder.append("Your score: ").append(multipleChoiceSubmittedAnswer.getScoreInPoints()).append("\n");
        for (var answerOption : question.getAnswerOptions()) {
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
            stringBuilder.append(answerOption.getText()).append("\t").append("\n");
        }
        return stringBuilder.toString();
    }

    private String createExportForShortAnswerQuestion(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        StringBuilder stringBuilder = new StringBuilder();
        ShortAnswerQuestion question = (ShortAnswerQuestion) shortAnswerSubmittedAnswer.getQuizQuestion();
        stringBuilder.append("Short Answer Question: ").append(question.getTitle()).append("\n");
        stringBuilder.append("Your score: ").append(shortAnswerSubmittedAnswer.getScoreInPoints()).append("\n");
        return replaceSpotWithSubmittedAnswer(shortAnswerSubmittedAnswer, stringBuilder);
    }

    private String replaceSpotWithSubmittedAnswer(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer, StringBuilder stringBuilder) {

        var spotToSubmittedTextMap = buildMapFromSpotsToSubmittedAnswers(shortAnswerSubmittedAnswer);
        stringBuilder.append("Your answer: ").append("\n");
        stringBuilder.append(shortAnswerSubmittedAnswer.getQuizQuestion().getText());
        for (Map.Entry<String, ShortAnswerSubmittedText> entry : spotToSubmittedTextMap.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey());
            Matcher matcher = pattern.matcher(stringBuilder);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                String replacement;
                if (entry.getValue().isIsCorrect()) {
                    replacement = entry.getValue().getText() + " (Correct)";
                }
                else {
                    replacement = entry.getValue().getText() + " (Incorrect)";
                }
                stringBuilder.replace(start, end, replacement);
                matcher = pattern.matcher(stringBuilder);
            }
        }
        return stringBuilder.toString();
    }

    private Map<String, ShortAnswerSubmittedText> buildMapFromSpotsToSubmittedAnswers(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        Map<String, ShortAnswerSubmittedText> spotsToSubmittedAnswers = new HashMap<>();
        for (var submittedText : shortAnswerSubmittedAnswer.getSubmittedTexts()) {
            int spotNr = submittedText.getSpot().getSpotNr();
            spotsToSubmittedAnswers.put("\\[-spot\\s*" + spotNr + "\\]", submittedText);

        }
        return spotsToSubmittedAnswers;
    }

    private void createCommunicationExport(List<Post> posts, List<AnswerPost> answerPosts, List<Reaction> reactions, long courseId, Path courseDir) throws IOException {
        var postsInCourse = posts.stream().filter(post -> courseId == post.getCoursePostingBelongsTo().getId()).toList();
        var answerPostsInCourse = answerPosts.stream().filter(answerPost -> courseId == answerPost.getCoursePostingBelongsTo().getId()).toList();
        var postReactionsInCourse = reactions.stream().filter(reaction -> reaction.getPost() != null)
                .filter(reaction -> courseId == reaction.getPost().getCoursePostingBelongsTo().getId()).toList();
        var answerPostReactionsInCourse = reactions.stream().filter(reaction -> reaction.getAnswerPost() != null)
                .filter(reaction -> courseId == reaction.getAnswerPost().getCoursePostingBelongsTo().getId()).toList();
        String[] headers = { "content/emoji", "creation date" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();
        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(courseDir.resolve("messages_posts_reactions" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.println();
            printer.print("Messages/Posts");
            printer.println();
            printer.println();
            for (var post : postsInCourse) {
                printer.printRecord(post.getContent(), post.getCreationDate());
            }
            printer.println();
            printer.print("Thread replies");
            printer.println();
            printer.println();
            for (var answerPost : answerPostsInCourse) {
                printer.printRecord(answerPost.getContent(), answerPost.getCreationDate());
            }
            printer.println();
            printer.print("Reactions");
            printer.println();
            printer.println();
            for (var reaction : postReactionsInCourse) {
                printer.printRecord(reaction.getEmojiId(), reaction.getCreationDate());
            }
            for (var reaction : answerPostReactionsInCourse) {
                printer.printRecord(reaction.getEmojiId(), reaction.getCreationDate());
            }

            printer.flush();

        }

    }

    private void storeModelingSubmissionContent(ModelingSubmission modelingSubmission, Path outputDir) throws IOException {
        if (modelingSubmission.getModel() != null) {
            try (var modelAsPdf = apollonConversionService.convertModel(modelingSubmission.getModel())) {
                Files.write(outputDir.resolve("submission_" + modelingSubmission.getId() + PDF_FILE_EXTENSION), modelAsPdf.readAllBytes());
            }
        }
        else {
            log.warn("Cannot include modeling submission content in data export because content is null for submission with id: {}", modelingSubmission.getId());

        }
    }

    private void storeTextSubmissionContent(TextSubmission textSubmission, Path outputDir) throws IOException {
        // text can be null which leads to an exception
        if (textSubmission.getText() != null) {
            Files.writeString(outputDir.resolve("text_exercise_submission_" + textSubmission.getId() + "_text.txt"), textSubmission.getText());
        }
        else {
            log.warn("Cannot include text submission content in data export because content is null for submission with id: {}", textSubmission.getId());
        }
    }

    private void createResultsTxtFile(Submission submission, Path outputDir) throws IOException {
        StringBuilder resultScoreAndFeedbacks = new StringBuilder();
        for (var result : submission.getResults()) {
            if (result != null) {
                resultScoreAndFeedbacks.append("Score of submission: ").append(result.getScore()).append("%").append(" ")
                        .append(result.getScore() * submission.getParticipation().getExercise().getMaxPoints() / 100).append(" Points").append("\n");
                if (submission instanceof ProgrammingSubmission && result.getPassedTestCaseCount() != null && result.getTestCaseCount() != null && result.getTestCaseCount() > 0) {
                    resultScoreAndFeedbacks.append("Passed test cases: ").append(result.getPassedTestCaseCount()).append("/").append(result.getTestCaseCount()).append("\n");
                }
                for (var feedback : result.getFeedbacks()) {
                    resultScoreAndFeedbacks.append("- Feedback: ");
                    // null if it's manual feedback
                    if (feedback.getText() != null) {
                        resultScoreAndFeedbacks.append(feedback.getText()).append("\t");
                    }
                    // null if the test case passes
                    if (feedback.getDetailText() != null) {
                        resultScoreAndFeedbacks.append(feedback.getDetailText()).append("\t");
                    }
                    resultScoreAndFeedbacks.append(feedback.getCredits()).append("\n");
                }
                Files.writeString(outputDir.resolve("submission_" + submission.getId() + "_result_" + result.getId() + TXT_FILE_EXTENSION), resultScoreAndFeedbacks);
            }
            resultScoreAndFeedbacks = new StringBuilder();
        }

    }

    private void createNonProgrammingExerciseExport(Exercise exercise, Path courseDir) throws IOException {
        Path exercisePath = courseDir.resolve(exercise.getSanitizedExerciseTitle());
        if (!Files.exists(exercisePath)) {
            Files.createDirectory(exercisePath);
        }
        createSubmissionsResultsExport(exercise, exercisePath);

    }

    private void copyFileUploadSubmissionFile(String submissionFilePath, Path outputDir) {
        try {
            FileUtils.copyDirectory(new File(submissionFilePath), outputDir.toFile());
        }
        catch (IOException ignored) {
            // ignore if we cannot retrieve the submitted file, it might no longer exist.
        }
    }

    private Path createDataExportZipFile(String userLogin) throws IOException {
        // There should actually never exist more than one data export for a user at a time (once the feature is fully implemented), but to be sure the name is unique, we add the
        // current timestamp
        return zipFileService.createZipFileWithFolderContent(dataExportPath.resolve("data-export_" + userLogin + ZonedDateTime.now().toEpochSecond() + ZIP_FILE_EXTENSION),
                workingDirectory, null);
    }

    private void createSubmissionCsvFile(Submission submission, Path outputPath) throws IOException {

        List<String> headers = new ArrayList<>(List.of("id", "submissionDate"));
        if (submission instanceof ProgrammingSubmission) {
            headers.add("commitHash");
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();

        try (final CSVPrinter printer = new CSVPrinter(
                Files.newBufferedWriter(outputPath.resolve("participation_" + submission.getParticipation().getId() + "_submission_" + submission.getId() + CSV_FILE_EXTENSION)),
                csvFormat)) {
            printer.printRecord(getSubmissionStreamToPrint(submission));
            printer.flush();

        }
    }

    private Stream<?> getSubmissionStreamToPrint(Submission submission) {
        var builder = Stream.builder();
        builder.add(submission.getId()).add(submission.getSubmissionDate()).add(submission.getType()).add(submission.getDurationInMinutes());
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            builder.add(programmingSubmission.getCommitHash());

        }
        else if (submission instanceof TextSubmission textSubmission) {
            builder.add(textSubmission.getText());
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            builder.add(modelingSubmission.getModel());
        }
        else if (submission instanceof QuizSubmission quizSubmission) {
            builder.add(quizSubmission.getScoreInPoints());
        }
        return builder.build();
    }

}
