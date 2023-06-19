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
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;

/**
 * Service Implementation for managing the data export in accordance with Art. 15 GDPR.
 */
@Service
public class DataExportService {

    private static final String ZIP_FILE_EXTENSION = ".zip";

    private static final String CSV_FILE_EXTENSION = ".csv";

    private static final String PDF_FILE_EXTENSION = ".pdf";

    private static final String TXT_FILE_EXTENSION = ".txt";

    private static final String COURSE_DIRECTORY_PREFIX = "course_";

    private static final String EXAM_DIRECTORY_PREFIX = "exam_";

    private final Logger log = LoggerFactory.getLogger(DataExportService.class);

    @Value("${artemis.data-export-path:./data-exports}")
    private Path dataExportPath;

    @Value("${artemis.repo-download-clone-path}")
    private Path repoClonePath;

    private final UserRepository userRepository;

    private final ZipFileService zipFileService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ExamService examService;

    private final DataExportRepository dataExportRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final DragAndDropQuizAnswerConversionService dragAndDropQuizAnswerConversionService;

    // Optional because otherwise the application doesn't start if the apollon profile is not set
    private final Optional<ApollonConversionService> apollonConversionService;

    private final StudentExamRepository studentExamRepository;

    private final FileService fileService;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ReactionRepository reactionRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final ComplaintRepository complaintRepository;

    public DataExportService(UserRepository userRepository, ZipFileService zipFileService, ProgrammingExerciseExportService programmingExerciseExportService,
            ExamService examService, DataExportRepository dataExportRepository, QuizQuestionRepository quizQuestionRepository, QuizSubmissionRepository quizSubmissionRepository,
            ExerciseRepository exerciseRepository, DragAndDropQuizAnswerConversionService dragAndDropQuizAnswerConversionService,
            Optional<ApollonConversionService> apollonConversionService, StudentExamRepository studentExamRepository, FileService fileService, PostRepository postRepository,
            AnswerPostRepository answerPostRepository, ReactionRepository reactionRepository, PlagiarismCaseRepository plagiarismCaseRepository,
            ComplaintRepository complaintRepository) {
        this.userRepository = userRepository;
        this.zipFileService = zipFileService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.examService = examService;
        this.dataExportRepository = dataExportRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.dragAndDropQuizAnswerConversionService = dragAndDropQuizAnswerConversionService;
        this.apollonConversionService = apollonConversionService;
        this.studentExamRepository = studentExamRepository;
        this.fileService = fileService;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.reactionRepository = reactionRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.complaintRepository = complaintRepository;
    }

    /**
     * Requests a data export for the given user.
     * This will create a new DataExport object in the database and start the creation of the data export.
     *
     * @return the created DataExport object
     */
    public DataExport requestDataExport() throws IOException {
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        dataExport.setUser(user);
        dataExport.setRequestDate(ZonedDateTime.now());
        if (!Files.exists(dataExportPath)) {
            Files.createDirectories(dataExportPath);
        }
        dataExport = dataExportRepository.save(dataExport);

        // ToDo: return from here directly and let the scheduler manage the pending exports in a work queue (part of a follow-up)
        var workingDirectory = Files.createTempDirectory(dataExportPath, "data-export-working-dir");

        dataExport.setDataExportState(DataExportState.IN_CREATION);
        dataExport = dataExportRepository.save(dataExport);

        var dataExportPath = createDataExport(user, workingDirectory);
        dataExport.setFilePath(dataExportPath.toString());
        // sending the email will be part of a follow-up, for now this just implies export finished
        dataExport.setCreationDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);

        return dataExportRepository.save(dataExport);
    }

    /**
     * Download the data export for the given data export id.
     *
     * @param dataExport the data export to download
     * @return the file path where the data export is stored
     */
    public Path downloadDataExport(DataExport dataExport) {
        dataExport.setDownloadDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport = dataExportRepository.save(dataExport);
        return Path.of(dataExport.getFilePath());
    }

    /**
     * Creates the data export for the given user.
     * Retrieves all courses and exercises the user has participated in from the database.
     *
     * @param user the user for which to create the data export
     * @return the path to the created data export
     */
    private Path createDataExport(User user, Path workingDirectory) throws IOException {
        var userId = user.getId();
        createExercisesExport(workingDirectory, userId);
        createExportForExams(userId, workingDirectory);
        createCommunicationExport(userId, workingDirectory);
        addGeneralUserInformation(user, workingDirectory);
        return createDataExportZipFile(user.getLogin(), workingDirectory);
    }

    private void createExercisesExport(Path workingDirectory, long userId) throws IOException {
        // retrieve all exercises as we cannot retrieve the exercises by course because a user might have participated in a course they are no longer a member of (they have
        // unenrolled)
        var allExerciseParticipations = exerciseRepository.getAllExercisesUserParticipatedInWithEagerParticipationsSubmissionsResultsFeedbacksByUserId(userId);
        var exerciseParticipationsPerCourse = allExerciseParticipations.stream().collect(Collectors.groupingBy(Exercise::getCourseViaExerciseGroupOrCourseMember));
        for (var entry : exerciseParticipationsPerCourse.entrySet()) {
            var course = entry.getKey();
            Path courseDir = retrieveCourseDirPath(workingDirectory, course);
            var exercises = entry.getValue();
            if (!exercises.isEmpty()) {
                Files.createDirectory(courseDir);
            }
            for (var exercise : entry.getValue()) {
                if (exercise instanceof ProgrammingExercise programmingExercise) {
                    createProgrammingExerciseExport(programmingExercise, courseDir, userId);
                }
                else {
                    createNonProgrammingExerciseExport(exercise, courseDir, userId);
                }
            }
        }
    }

    private void createExportForExams(long userId, Path workingDirectory) throws IOException {
        Map<Course, List<StudentExam>> studentExamsPerCourse = studentExamRepository.findAllWithExercisesParticipationsSubmissionsResultsAndFeedbacksByUserId(userId).stream()
                .collect(Collectors.groupingBy(studentExam -> studentExam.getExam().getCourse()));

        for (var entry : studentExamsPerCourse.entrySet()) {
            for (var studentExam : entry.getValue()) {
                var exam = studentExam.getExam();
                var examTitle = exam.getSanitizedExamTitle();
                var courseDirPath = retrieveCourseDirPath(workingDirectory, exam.getCourse());
                var examDirectoryName = EXAM_DIRECTORY_PREFIX + examTitle + "_" + studentExam.getId();
                var examWorkingDir = Files.createDirectories(courseDirPath.resolve(examDirectoryName));
                createStudentExamExport(studentExam, examWorkingDir);
            }
        }
    }

    private void createStudentExamExport(StudentExam studentExam, Path examWorkingDir) throws IOException {
        for (var exercise : studentExam.getExercises()) {
            // since the behavior is undefined if multiple student exams for the same exam and student combination exist, the exercise can be null
            if (exercise == null) {
                continue;
            }
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                createProgrammingExerciseExport(programmingExercise, examWorkingDir, studentExam.getUser().getId());
            }
            else {
                createNonProgrammingExerciseExport(exercise, examWorkingDir, studentExam.getUser().getId());
            }
        }
        // leave out the results if the results are not published yet to avoid leaking information through the data export
        if (studentExam.areResultsPublishedYet()) {
            addExamScores(studentExam, examWorkingDir);
        }
        addGeneralExamInformation(studentExam, examWorkingDir);
    }

    private void addExamScores(StudentExam studentExam, Path examWorkingDir) throws IOException {
        var studentExamGrade = examService.getStudentExamGradeForDataExport(studentExam);
        var studentResult = studentExamGrade.studentResult();
        List<String> headers = new ArrayList<>();
        var examResults = getExamResultsStreamToPrint(studentResult, headers);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();
        var examTitle = studentExam.getExam().getSanitizedExamTitle();
        var examResultsFileName = EXAM_DIRECTORY_PREFIX + examTitle + "_" + studentExam.getId() + "_result" + CSV_FILE_EXTENSION;
        try (final var printer = new CSVPrinter(Files.newBufferedWriter(examWorkingDir.resolve(examResultsFileName)), csvFormat)) {
            printer.printRecord(examResults);
            printer.flush();
        }
    }

    private Stream<?> getExamResultsStreamToPrint(ExamScoresDTO.StudentResult studentResult, List<String> headers) {
        var builder = Stream.builder();
        if (studentResult.overallPointsAchieved() != null) {
            builder.add(studentResult.overallPointsAchieved());
            headers.add("overall points");
        }
        if (studentResult.hasPassed() != null) {
            builder.add(studentResult.hasPassed());
            headers.add("passed");
        }
        if (studentResult.overallGrade() != null) {
            builder.add(studentResult.overallGrade());
            headers.add("overall grade");
        }
        if (studentResult.gradeWithBonus() != null) {
            builder.add(studentResult.gradeWithBonus());
            headers.add("grade with bonus");
        }
        if (studentResult.overallScoreAchieved() != null) {
            builder.add(studentResult.overallScoreAchieved());
            headers.add("overall score (%)");
        }
        return builder.build();
    }

    private void addGeneralExamInformation(StudentExam studentExam, Path examWorkingDir) throws IOException {
        String[] headers = new String[] { "started", "testExam", "started at", "submitted", "submitted at", "working time (in minutes)", "individual end date" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();
        var examTitle = studentExam.getExam().getSanitizedExamTitle();
        var examGeneralInformationFileName = EXAM_DIRECTORY_PREFIX + examTitle + "_" + studentExam.getId() + CSV_FILE_EXTENSION;
        try (final var printer = new CSVPrinter(Files.newBufferedWriter(examWorkingDir.resolve(examGeneralInformationFileName)), csvFormat)) {
            printer.printRecord(studentExam.isStarted(), studentExam.isTestExam(), studentExam.getStartedDate(), studentExam.isSubmitted(), studentExam.getSubmissionDate(),
                    studentExam.getWorkingTime() / 60, studentExam.getIndividualEndDate());
            printer.flush();
        }
    }

    private void addGeneralUserInformation(User user, Path workingDirectory) throws IOException {
        String[] headers = new String[] { "login", "name", "email", "registration number" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();

        try (final var printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("general_user_information" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(user.getLogin(), user.getName(), user.getEmail(), user.getRegistrationNumber());
            printer.flush();
        }
    }

    private void createProgrammingExerciseExport(ProgrammingExercise programmingExercise, Path courseDir, long userId) throws IOException {
        Path exerciseDir = courseDir.resolve(programmingExercise.getSanitizedExerciseTitle());
        createDirectoryIfNotExistent(exerciseDir);
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
        var tempRepoWorkingDir = fileService.getUniquePath(repoClonePath.toString());
        programmingExerciseExportService.exportStudentRepositories(programmingExercise, listOfProgrammingExerciseParticipations, repositoryExportOptions, tempRepoWorkingDir,
                exerciseDir, exportRepoErrors);
        // we use this directory only to clone the repository and don't do this in our current directory because the current directory is part of the final data export
        // --> we can delete it after use
        fileService.scheduleForDirectoryDeletion(tempRepoWorkingDir, 5);
        createPlagiarismCaseInfoExport(programmingExercise, exerciseDir, userId);

    }

    private void createSubmissionsResultsExport(Exercise exercise, Path exerciseDir) throws IOException {
        boolean includeResults = exercise.isExamExercise() && exercise.getExamViaExerciseGroupOrCourseMember().resultsPublished()
                || exercise.isCourseExercise() && exercise.isAssessmentDueDateOver();
        for (var participation : exercise.getStudentParticipations()) {
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
                    createQuizAnswersExport((QuizExercise) exercise, participation, exerciseDir, includeResults);
                }
                if (includeResults) {
                    createResultsAndComplaintTxtFile(submission, exerciseDir);
                }
            }
        }
    }

    private void createQuizAnswersExport(QuizExercise quizExercise, StudentParticipation participation, Path outputDir, boolean includeResults) throws IOException {
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
                        dragAndDropQuizAnswerConversionService.convertDragAndDropQuizAnswerAndStoreAsPdf(dragAndDropSubmittedAnswer, outputDir, includeResults);
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
                Files.write(outputDir.resolve("quiz_submission_" + submission.getId() + "_multiple_choice_questions_answers" + TXT_FILE_EXTENSION),
                        multipleChoiceQuestionsSubmissions);
            }
            if (!shortAnswerQuestionsSubmissions.isEmpty()) {
                Files.write(outputDir.resolve("quiz_submission_" + submission.getId() + "_short_answer_questions_answers" + TXT_FILE_EXTENSION), shortAnswerQuestionsSubmissions);
            }
        }

    }

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
            stringBuilder.append(answerOption.getText()).append("\t").append("\n");
        }
        return stringBuilder.toString();
    }

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

    private static void addExplanationToAnswerOptionWithResult(MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer, StringBuilder stringBuilder,
            AnswerOption answerOption) {
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

    private String createExportForShortAnswerQuestion(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer, boolean includeResults) {
        StringBuilder stringBuilder = new StringBuilder();
        ShortAnswerQuestion question = (ShortAnswerQuestion) shortAnswerSubmittedAnswer.getQuizQuestion();
        stringBuilder.append("Short Answer Question: ").append(question.getTitle()).append("\n");
        if (includeResults) {
            stringBuilder.append("Your score: ").append(shortAnswerSubmittedAnswer.getScoreInPoints()).append("\n");
        }
        return replaceSpotWithSubmittedAnswer(shortAnswerSubmittedAnswer, stringBuilder, includeResults);
    }

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

    private Matcher addSubmittedAnswerWithResult(StringBuilder submittedAnswer, boolean includeResults, ShortAnswerSubmittedText submittedText, Pattern pattern, Matcher matcher,
            StringBuilder replacement) {
        int start = matcher.start();
        int end = matcher.end();
        if (submittedText.isIsCorrect() != null && submittedText.isIsCorrect()) {
            replacement.append(submittedText.getText());
            if (includeResults) {
                replacement.append(" (Correct)");
            }
        }
        else if (submittedText.isIsCorrect() != null && !submittedText.isIsCorrect()) {
            replacement.append(submittedText.getText());
            if (includeResults) {
                replacement.append(" (Incorrect)");
            }
            else {
                replacement.append(submittedText.getText());
            }
            submittedAnswer.replace(start, end, replacement.toString());
            matcher = pattern.matcher(submittedAnswer);
        }
        return matcher;
    }

    private Map<String, ShortAnswerSubmittedText> buildMapFromSpotsToSubmittedAnswers(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        Map<String, ShortAnswerSubmittedText> spotsToSubmittedAnswers = new HashMap<>();
        for (var submittedText : shortAnswerSubmittedAnswer.getSubmittedTexts()) {
            int spotNr = submittedText.getSpot().getSpotNr();
            spotsToSubmittedAnswers.put("\\[-spot\\s*" + spotNr + "\\]", submittedText);

        }
        return spotsToSubmittedAnswers;
    }

    private void createCommunicationExport(long userId, Path workingDirectory) throws IOException {
        var postsPerCourse = postRepository.findPostsByAuthorId(userId).stream().filter(post -> post.getCoursePostingBelongsTo() != null)
                .collect(Collectors.groupingBy(Post::getCoursePostingBelongsTo));
        // plagiarism case posts are included in the plagiarism case export
        var answerPostsPerCourse = answerPostRepository.findAnswerPostsByAuthorId(userId).stream().filter(answerPost -> answerPost.getCoursePostingBelongsTo() != null)
                .filter(answerPost -> answerPost.getPost().getPlagiarismCase() == null).collect(Collectors.groupingBy(AnswerPost::getCoursePostingBelongsTo));
        var reactions = reactionRepository.findReactionsByUserId(userId);
        var reactionsToPostsPerCourse = reactions.stream().filter(reaction -> reaction.getPost() != null).filter(reaction -> reaction.getPost().getCoursePostingBelongsTo() != null)
                .collect(Collectors.groupingBy(reaction -> reaction.getPost().getCoursePostingBelongsTo()));
        var reactionsToAnswerPostsPerCourse = reactions.stream().filter(reaction -> reaction.getAnswerPost() != null)
                .filter(reaction -> reaction.getAnswerPost().getCoursePostingBelongsTo() != null)
                .collect(Collectors.groupingBy(reaction -> reaction.getAnswerPost().getCoursePostingBelongsTo()));
        createCommunicationExportIfPostsExist(workingDirectory, postsPerCourse, answerPostsPerCourse, reactionsToPostsPerCourse, reactionsToAnswerPostsPerCourse);
        createCommunicationExportIfAnswerPostsExist(workingDirectory, answerPostsPerCourse, reactionsToPostsPerCourse, reactionsToAnswerPostsPerCourse);
        createCommunicationExportIfReactionsToPostsExist(workingDirectory, reactionsToPostsPerCourse, reactionsToAnswerPostsPerCourse);
        createCommunicationExportIfReactionsToAnswerPostsExist(workingDirectory, reactionsToAnswerPostsPerCourse);
    }

    private void createCommunicationExportIfReactionsToAnswerPostsExist(Path workingDirectory, Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // it can happen that only answer post reactions exist in a course but neither posts, nor answer posts nor reactions to posts
        for (var entry : reactionsToAnswerPostsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var answerPostReactionsInCourse = entry.getValue();
            createDirectoryIfNotExistent(courseDir);
            createCommunicationCsvFile(courseDir, List.of(), List.of(), List.of(), answerPostReactionsInCourse);
        }
    }

    private void createCommunicationExportIfReactionsToPostsExist(Path workingDirectory, Map<Course, List<Reaction>> reactionsToPostsPerCourse,
            Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // it can happen that only reactions exist in a course but no post or answer post
        for (var entry : reactionsToPostsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var postReactionsInCourse = entry.getValue();
            var answerPostReactionsInCourse = reactionsToAnswerPostsPerCourse.remove(course);
            createDirectoryIfNotExistent(courseDir);
            createCommunicationCsvFile(courseDir, List.of(), List.of(), postReactionsInCourse, answerPostReactionsInCourse == null ? List.of() : answerPostReactionsInCourse);
        }
    }

    private void createCommunicationExportIfAnswerPostsExist(Path workingDirectory, Map<Course, List<AnswerPost>> answerPostsPerCourse,
            Map<Course, List<Reaction>> reactionsToPostsPerCourse, Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // it can happen that an answer post and reactions exist in a course but no post
        for (var entry : answerPostsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var answerPostsInCourse = entry.getValue();
            var postReactionsInCourse = reactionsToPostsPerCourse.remove(course);
            var answerPostReactionsInCourse = reactionsToAnswerPostsPerCourse.remove(course);
            createDirectoryIfNotExistent(courseDir);
            createCommunicationCsvFile(courseDir, List.of(), answerPostsInCourse == null ? List.of() : answerPostsInCourse,
                    postReactionsInCourse == null ? List.of() : postReactionsInCourse, answerPostReactionsInCourse == null ? List.of() : answerPostReactionsInCourse);
        }
    }

    private void createCommunicationExportIfPostsExist(Path workingDirectory, Map<Course, List<Post>> postsPerCourse, Map<Course, List<AnswerPost>> answerPostsPerCourse,
            Map<Course, List<Reaction>> reactionsToPostsPerCourse, Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // this covers all cases where at least one post in a course exists
        for (var entry : postsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var postsInCourse = entry.getValue();
            // we remove them, so we do not iterate over them again below.
            var answerPostsInCourse = answerPostsPerCourse.remove(course);
            var postReactionsInCourse = reactionsToPostsPerCourse.remove(course);
            var answerPostReactionsInCourse = reactionsToAnswerPostsPerCourse.remove(course);
            createDirectoryIfNotExistent(courseDir);
            createCommunicationCsvFile(courseDir, postsInCourse, answerPostsInCourse == null ? List.of() : answerPostsInCourse,
                    postReactionsInCourse == null ? List.of() : postReactionsInCourse, answerPostReactionsInCourse == null ? List.of() : answerPostReactionsInCourse);
        }
    }

    private void createDirectoryIfNotExistent(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectory(directory);
        }
    }

    private Path retrieveCourseDirPath(Path workingDirectory, Course course) {
        return workingDirectory.resolve(COURSE_DIRECTORY_PREFIX + course.getShortName());
    }

    private void createCommunicationCsvFile(Path courseDir, List<Post> postsInCourse, List<AnswerPost> answerPostsInCourse, List<Reaction> postReactionsInCourse,
            List<Reaction> answerPostReactionsInCourse) throws IOException {
        String[] headers = { "content/emoji", "creation date", "post content reaction/reply belongs to" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();
        try (final var printer = new CSVPrinter(Files.newBufferedWriter(courseDir.resolve("messages_posts_reactions" + CSV_FILE_EXTENSION)), csvFormat)) {
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
                printer.printRecord(answerPost.getContent(), answerPost.getCreationDate(), answerPost.getPost().getContent());
            }
            printer.println();
            printer.print("Reactions");
            printer.println();
            printer.println();
            for (var reaction : postReactionsInCourse) {
                printer.printRecord(reaction.getEmojiId(), reaction.getCreationDate(), reaction.getPost().getContent());
            }
            for (var reaction : answerPostReactionsInCourse) {
                printer.printRecord(reaction.getEmojiId(), reaction.getCreationDate(), reaction.getAnswerPost().getContent());
            }
            printer.flush();
        }
    }

    private void storeModelingSubmissionContent(ModelingSubmission modelingSubmission, Path outputDir) throws IOException {
        if (modelingSubmission.getModel() == null) {
            log.warn("Cannot include modeling submission content in data export because content is null for submission with id: {}", modelingSubmission.getId());
            return;
        }
        var fileName = "submission_" + modelingSubmission.getId();
        if (apollonConversionService.isEmpty()) {
            log.warn("Cannot include modeling submission content in data export as pdf because apollon profile is not active. Going to include the json file");
            addModelJsonWithExplanationHowToView(modelingSubmission.getModel(), outputDir, fileName);
            return;
        }

        try (var modelAsPdf = apollonConversionService.get().convertModel(modelingSubmission.getModel())) {
            Files.write(outputDir.resolve(fileName + PDF_FILE_EXTENSION), modelAsPdf.readAllBytes());
        }

    }

    private void addModelJsonWithExplanationHowToView(String model, Path outputDir, String fileName) throws IOException {
        Files.writeString(outputDir.resolve(fileName + ".json"), model);
        String explanation = """
                You can view your model if you go to [Apollon Modeling Editor](https://www.apollon.ase.in.tum.de) and click on File --> Import and select the .json file.
                """;
        Files.writeString(outputDir.resolve("view_model.md"), explanation);
    }

    private void storeTextSubmissionContent(TextSubmission textSubmission, Path outputDir) throws IOException {
        // text can be null which leads to an exception
        if (textSubmission.getText() != null) {
            Files.writeString(outputDir.resolve("text_exercise_submission_" + textSubmission.getId() + "_text" + TXT_FILE_EXTENSION), textSubmission.getText());
        }
        else {
            log.warn("Cannot include text submission content in data export because content is null for submission with id: {}", textSubmission.getId());
        }
    }

    private void createResultsAndComplaintTxtFile(Submission submission, Path outputDir) throws IOException {
        StringBuilder resultScoreAndFeedbacks = new StringBuilder();
        for (var result : submission.getResults()) {
            if (result != null) {
                var score = result.getScore();
                if (score != null) {
                    resultScoreAndFeedbacks.append("Score of submission: ").append(score).append("%").append(" ")
                            .append(score * submission.getParticipation().getExercise().getMaxPoints() / 100).append(" Points").append("\n");
                }
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
        var possibleComplaint = complaintRepository.findWithEagerComplaintResponseByResultSubmissionId(submission.getId());
        if (possibleComplaint.isPresent()) {
            addComplaintData(possibleComplaint.get(), outputDir);
        }
    }

    private void addComplaintData(Complaint complaint, Path outputDir) throws IOException {
        List<String> headers = new ArrayList<>();
        var dataStreamBuilder = Stream.builder();
        headers.add("id");
        dataStreamBuilder.add(complaint.getId());
        headers.add("submitted at");
        dataStreamBuilder.add(complaint.getSubmittedTime());
        headers.add("complaint type");
        dataStreamBuilder.add(complaint.getComplaintType());
        headers.add("complaint text");
        dataStreamBuilder.add(complaint.getComplaintText());

        if (complaint.getComplaintResponse() != null) {
            headers.add("complaint response");
            dataStreamBuilder.add(complaint.getComplaintResponse().getResponseText());
        }
        if (complaint.isAccepted() != null && ComplaintType.COMPLAINT == complaint.getComplaintType()) {
            headers.add("accepted");
            dataStreamBuilder.add(complaint.isAccepted());
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();
        var prefix = complaint.getComplaintType() == ComplaintType.COMPLAINT ? "complaint_" : "more_feedback_";

        try (final var printer = new CSVPrinter(Files.newBufferedWriter(outputDir.resolve(prefix + complaint.getId() + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(dataStreamBuilder.build());
            printer.flush();
        }
    }

    private void createNonProgrammingExerciseExport(Exercise exercise, Path courseDir, long userId) throws IOException {
        Path exercisePath = courseDir.resolve(exercise.getSanitizedExerciseTitle());
        createDirectoryIfNotExistent(exercisePath);
        createSubmissionsResultsExport(exercise, exercisePath);
        createPlagiarismCaseInfoExport(exercise, exercisePath, userId);

    }

    private void createPlagiarismCaseInfoExport(Exercise exercise, Path exercisePath, long userId) throws IOException {
        var plagiarismCaseOptional = plagiarismCaseRepository.findByStudentIdAndExerciseIdWithPostAndAnswerPost(userId, exercise.getId());
        List<String> headers = new ArrayList<>();
        var dataStreamBuilder = Stream.builder();
        if (plagiarismCaseOptional.isEmpty()) {
            return;
        }
        var plagiarismCase = plagiarismCaseOptional.get();
        if (plagiarismCase.getVerdict() != null) {
            headers.add("Verdict");
            headers.add("Verdict Date");
            dataStreamBuilder.add(plagiarismCase.getVerdict());
            dataStreamBuilder.add(plagiarismCase.getVerdictDate());
        }
        if (plagiarismCase.getPost() != null) {
            headers.add("Plagiarism case announcement");
            dataStreamBuilder.add(plagiarismCase.getPost().getContent());
        }
        if (!plagiarismCase.getPost().getAnswers().isEmpty()) {
            headers.add("Plagiarism case replies");
            dataStreamBuilder.add(plagiarismCase.getPost().getAnswers().stream().map(AnswerPost::getContent).collect(Collectors.joining("\n")));
        }
        if (plagiarismCase.getVerdict() == PlagiarismVerdict.POINT_DEDUCTION) {
            dataStreamBuilder.add(plagiarismCase.getVerdictPointDeduction());
        }
        else if (plagiarismCase.getVerdict() == PlagiarismVerdict.WARNING) {
            dataStreamBuilder.add(plagiarismCase.getVerdictMessage());
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();

        try (final var printer = new CSVPrinter(Files.newBufferedWriter(exercisePath.resolve("plagiarism_case_" + plagiarismCase.getId() + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(dataStreamBuilder.build());
            printer.flush();
        }

    }

    private void copyFileUploadSubmissionFile(String submissionFilePath, Path outputDir) {
        try {
            FileUtils.copyDirectory(new File(submissionFilePath), outputDir.toFile());
        }
        catch (IOException ignored) {
            log.warn("Cannot include file {} as file upload submission because the file doesn't exist anymore.", submissionFilePath);
            // ignore if we cannot retrieve the submitted file, it might no longer exist.
        }
    }

    private Path createDataExportZipFile(String userLogin, Path workingDirectory) throws IOException {
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

        try (final var printer = new CSVPrinter(
                Files.newBufferedWriter(outputPath.resolve("participation_" + submission.getParticipation().getId() + "_submission_" + submission.getId() + CSV_FILE_EXTENSION)),
                csvFormat)) {
            printer.printRecord(getSubmissionStreamToPrint(submission));
            printer.flush();
        }
    }

    private Stream<?> getSubmissionStreamToPrint(Submission submission) {
        var builder = Stream.builder();
        builder.add(submission.getId()).add(submission.getSubmissionDate());
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            builder.add(programmingSubmission.getCommitHash());
        }
        return builder.build();
    }
}
