package de.tum.cit.aet.artemis.quiz.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.repository.DragAndDropMappingRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizScheduleService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to quiz exercises for use in integration tests.
 */
@Service
public class QuizExerciseUtilService {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuizBatchRepository quizBatchRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private SubmittedAnswerRepository submittedAnswerRepository;

    @Autowired
    private DragAndDropMappingRepository dragAndDropMappingRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private QuizScheduleService quizScheduleService;

    /**
     * Creates and saves a course with one quiz exercise with the title "Title".
     * The quiz is synchronized and has a duration of 120 seconds.
     *
     * @return The created course with the quiz.
     */
    public Course addCourseWithOneQuizExercise() {
        return addCourseWithOneQuizExercise("Title");
    }

    /**
     * Creates and saves a course with one quiz exercise with the given title.
     * The quiz is synchronized and has a duration of 120 seconds.
     *
     * @param title The title of the quiz exercise.
     * @return The newly created course with the quiz.
     */
    public Course addCourseWithOneQuizExercise(String title) {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, QuizMode.SYNCHRONIZED);
        quizExercise.setTitle(title);
        quizExercise.setDuration(120);
        assertThat(quizExercise.getQuizQuestions()).isNotEmpty();
        assertThat(quizExercise.isValid()).isTrue();
        course.addExercises(quizExercise);
        course = courseRepo.save(course);
        quizExercise = exerciseRepo.save(quizExercise);
        assertThat(courseRepo.findWithEagerExercisesById(course.getId()).getExercises()).as("course contains the exercise").contains(quizExercise);
        return course;
    }

    /**
     * Creates and adds a student participation to the given quiz submission. The submission is then saved in the repository.
     *
     * @param quizExercise The quiz for which a student participation should be created.
     * @param submission   The submission which should be saved
     * @param login        The login of the user participating in the quiz.
     * @return The saved submission.
     */
    public QuizSubmission saveQuizSubmission(QuizExercise quizExercise, QuizSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(quizExercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        return quizSubmissionRepository.save(submission);
    }

    /**
     * Sets quiz exercise dates and course to null so the quiz can be imported. The quiz batches are set to an empty set.
     *
     * @param quizExercise The quiz of which fields should be set to null.
     */
    public void emptyOutQuizExercise(QuizExercise quizExercise) {
        quizExercise.setReleaseDate(null);
        quizExercise.setDueDate(null);
        quizExercise.setAssessmentDueDate(null);
        quizExercise.setCourse(null);
        quizExercise.setQuizBatches(new HashSet<>());
    }

    /**
     * Creates and saves a new quiz exercise.
     *
     * @param releaseDate The release date of the quiz, also used to set the start date of the course.
     * @param dueDate     The due date of the quiz, also used to set the end date of the course.
     * @param quizMode    The mode of the quiz. SYNCHRONIZED, BATCHED or INDIVIDUAL.
     * @return The created quiz exercise.
     */
    public QuizExercise createAndSaveQuiz(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        QuizExercise quizExercise = createQuiz(releaseDate, dueDate, quizMode);
        quizExerciseRepository.save(quizExercise);

        return quizExercise;
    }

    /**
     * Creates and saves a course. A new quiz exercise is created and added to the course.
     *
     * @param releaseDate The release date of the quiz, also used to set the start date of the course.
     * @param dueDate     The due date of the quiz, also used to set the end date of the course.
     * @param quizMode    The mode of the quiz. SYNCHRONIZED, BATCHED, or INDIVIDUAL.
     * @return The created quiz exercise.
     */
    public QuizExercise createQuiz(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        Course course = courseUtilService.createAndSaveCourse(null, releaseDate == null ? null : releaseDate.minusDays(1), dueDate == null ? null : dueDate.plusDays(1), Set.of());

        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, dueDate, quizMode, course);
        QuizExerciseFactory.addQuestionsToQuizExercise(quizExercise);

        return quizExercise;
    }

    /**
     * Creates and saves a team quiz exercise.
     *
     * @param releaseDate The release date of the quiz.
     * @param dueDate     The due date of the quiz.
     * @param quizMode    The mode of the quiz. SYNCHRONIZED, BATCHED or INDIVIDUAL
     * @param minTeamSize The minimum number of members the team is allowed to have.
     * @param maxTeamSize The maximum number of members the team is allowed to have.
     * @return The created quiz exercise.
     */
    public QuizExercise createAndSaveTeamQuiz(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode, int minTeamSize, int maxTeamSize) {
        QuizExercise quizExercise = createQuiz(releaseDate, dueDate, quizMode);
        setupTeamQuizExercise(quizExercise, minTeamSize, maxTeamSize);
        quizExerciseRepository.save(quizExercise);

        Team team = new Team();
        team.setShortName("team");
        teamRepo.save(quizExercise, team);

        return quizExercise;
    }

    /**
     * Sets up a team quiz exercise by creating the team assignment config with the passed values and setting it to the quiz.
     *
     * @param quiz        The quiz which should be a team exercise.
     * @param minTeamSize The minimum number of members the team is allowed to have.
     * @param maxTeamSize The maximum number of members the team is allowed to have.
     */
    public void setupTeamQuizExercise(QuizExercise quiz, int minTeamSize, int maxTeamSize) {
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(quiz);
        teamAssignmentConfig.setMinTeamSize(minTeamSize);
        teamAssignmentConfig.setMaxTeamSize(maxTeamSize);
        quiz.setMode(ExerciseMode.TEAM);
        quiz.setTeamAssignmentConfig(teamAssignmentConfig);
    }

    /**
     * Creates and saves a course and an exam. An exam quiz exercise is created and saved.
     *
     * @param startDate The start date of the exam, also used to set the start date of the course the exam is in.
     * @param endDate   The end date of the exam, also used to set the end date of the course the exam is in.
     * @return The created exam quiz exercise.
     */
    @NotNull
    public QuizExercise createAndSaveExamQuiz(ZonedDateTime startDate, ZonedDateTime endDate) {
        Course course = courseUtilService.createAndSaveCourse(null, startDate.minusDays(1), endDate.plusDays(1), new HashSet<>());

        Exam exam = ExamFactory.generateExam(course, startDate.minusMinutes(5), startDate, endDate, false);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam);
        examRepository.save(exam);

        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExerciseForExam(exerciseGroup);
        QuizExerciseFactory.addQuestionsToQuizExercise(quizExercise);

        quizExerciseRepository.save(quizExercise);

        return quizExercise;
    }

    /**
     * Renames and saves the quiz exercise using the passed title.
     *
     * @param quizExercise The quiz to be renamed.
     * @param newTitle     The new name of the quiz.
     */
    public void renameAndSaveQuiz(QuizExercise quizExercise, String newTitle) {
        quizExercise.setTitle(newTitle);
        quizExerciseRepository.save(quizExercise);
    }

    /**
     * Sets the quiz exercise of the quiz batch and saves the batch into the repository.
     *
     * @param batch        The quiz batch which should be saved.
     * @param quizExercise The quiz exercise to be added to the batch.
     */
    public void setQuizBatchExerciseAndSave(QuizBatch batch, QuizExercise quizExercise) {
        batch.setQuizExercise(quizExercise);
        quizBatchRepository.save(batch);
    }

    /**
     * Creates and saves a quiz with a multiple, single choice, short, and drag and drop question.
     * An actual picture file is used as the background and a data item of the drag and drop question.
     * The quiz takes 120 seconds and is synchronized.
     * A participation and submission are also created for the user with the given login. An answer is submitted for each question.
     *
     * @param course             The course of the quiz.
     * @param login              The login of the user participating in the quiz.
     * @param dueDateInTheFuture True, if the due date of the quiz is in the future.
     * @return The created quiz submission.
     * @throws IOException If the background or data item file cannot be accessed.
     */
    public QuizSubmission addQuizExerciseToCourseWithParticipationAndSubmissionForUser(Course course, String login, boolean dueDateInTheFuture) throws IOException {
        QuizExercise quizExercise;
        if (dueDateInTheFuture) {
            quizExercise = createAndSaveQuizWithAllQuestionTypes(course, PAST_TIMESTAMP, FUTURE_TIMESTAMP, FUTURE_TIMESTAMP, QuizMode.SYNCHRONIZED);
        }
        else {
            quizExercise = createAndSaveQuizWithAllQuestionTypes(course, PAST_TIMESTAMP, PAST_TIMESTAMP, PAST_TIMESTAMP, QuizMode.SYNCHRONIZED);
        }
        quizExercise.setTitle("quiz");
        quizExercise.setDuration(120);

        course.addExercises(quizExercise);
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setExercise(quizExercise);
        studentParticipation.setParticipant(userUtilService.getUserByLogin(login));
        QuizSubmission quizSubmission = new QuizSubmission();
        quizSubmission.setParticipation(studentParticipation);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.setScoreInPoints(2.0);
        var submittedAnswerMC = new MultipleChoiceSubmittedAnswer();
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) (quizExercise.getQuizQuestions().getFirst());
        submittedAnswerMC.setSelectedOptions(Set.of(multipleChoiceQuestion.getAnswerOptions().getFirst(), multipleChoiceQuestion.getAnswerOptions().get(1)));
        submittedAnswerMC.setQuizQuestion(multipleChoiceQuestion);
        var submittedAnswerSC = new MultipleChoiceSubmittedAnswer();
        MultipleChoiceQuestion singleChoiceQuestion = (MultipleChoiceQuestion) (quizExercise.getQuizQuestions().get(3));
        submittedAnswerSC.setSelectedOptions(Set.of(multipleChoiceQuestion.getAnswerOptions().getFirst()));
        submittedAnswerSC.setQuizQuestion(singleChoiceQuestion);
        var submittedShortAnswer = new ShortAnswerSubmittedAnswer();
        ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) (quizExercise.getQuizQuestions().get(2));
        submittedShortAnswer.setQuizQuestion(shortAnswerQuestion);
        ShortAnswerSubmittedText shortAnswerSubmittedText1 = new ShortAnswerSubmittedText();
        ShortAnswerSubmittedText shortAnswerSubmittedText2 = new ShortAnswerSubmittedText();
        shortAnswerQuestion.setExercise(quizExercise);
        shortAnswerSubmittedText1.setText("my text");
        shortAnswerSubmittedText1.setIsCorrect(false);
        shortAnswerSubmittedText2.setText("is");
        shortAnswerSubmittedText2.setIsCorrect(true);
        shortAnswerSubmittedText1.setSubmittedAnswer(submittedShortAnswer);
        shortAnswerSubmittedText2.setSubmittedAnswer(submittedShortAnswer);
        submittedShortAnswer.addSubmittedTexts(shortAnswerSubmittedText1);
        submittedShortAnswer.addSubmittedTexts(shortAnswerSubmittedText2);
        shortAnswerSubmittedText1.setSpot(shortAnswerQuestion.getSpots().get(1));
        shortAnswerSubmittedText2.setSpot(shortAnswerQuestion.getSpots().getFirst());

        var submittedDragAndDropAnswer = new DragAndDropSubmittedAnswer();
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) (quizExercise.getQuizQuestions().get(1));
        var backgroundPathInFileSystem = FilePathService.getDragAndDropBackgroundFilePath().resolve("drag_and_drop_background.jpg");
        var dragItemPathInFileSystem = FilePathService.getDragItemFilePath().resolve("drag_item.jpg");
        if (Files.exists(backgroundPathInFileSystem)) {
            Files.delete(backgroundPathInFileSystem);
        }
        if (Files.exists(dragItemPathInFileSystem)) {
            Files.delete(dragItemPathInFileSystem);
        }
        FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/data-export/drag_and_drop_background.jpg"), backgroundPathInFileSystem.toFile());
        FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/data-export/drag_item.jpg"), dragItemPathInFileSystem.toFile());
        dragAndDropQuestion.setBackgroundFilePath("/api/files/drag-and-drop/backgrounds/3/drag_and_drop_background.jpg");
        submittedDragAndDropAnswer.setQuizQuestion(dragAndDropQuestion);
        dragAndDropQuestion.setExercise(quizExercise);
        DragAndDropMapping dragAndDropMapping = new DragAndDropMapping();
        dragAndDropMapping.setDragItem(dragAndDropQuestion.getDragItems().getFirst());
        dragAndDropMapping.setDropLocation(dragAndDropQuestion.getDropLocations().getFirst());
        DragAndDropMapping incorrectDragAndDropMapping = new DragAndDropMapping();
        incorrectDragAndDropMapping.setDragItem(dragAndDropQuestion.getDragItems().get(3));
        incorrectDragAndDropMapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(1));
        DragAndDropMapping mappingWithImage = new DragAndDropMapping();
        mappingWithImage.setDragItem(dragAndDropQuestion.getDragItems().get(4));
        mappingWithImage.setDropLocation(dragAndDropQuestion.getDropLocations().get(3));
        dragAndDropQuestion.addCorrectMapping(dragAndDropMapping);
        studentParticipationRepository.save(studentParticipation);
        quizSubmissionRepository.save(quizSubmission);
        submittedShortAnswer.setSubmission(quizSubmission);
        submittedAnswerMC.setSubmission(quizSubmission);
        submittedAnswerSC.setSubmission(quizSubmission);
        submittedDragAndDropAnswer.setSubmission(quizSubmission);
        dragAndDropMapping.setSubmittedAnswer(submittedDragAndDropAnswer);
        incorrectDragAndDropMapping.setSubmittedAnswer(submittedDragAndDropAnswer);
        mappingWithImage.setSubmittedAnswer(submittedDragAndDropAnswer);
        submittedAnswerRepository.save(submittedDragAndDropAnswer);
        dragAndDropMapping.setQuestion(null);
        incorrectDragAndDropMapping.setQuestion(null);
        mappingWithImage.setQuestion(null);
        dragAndDropMapping = dragAndDropMappingRepository.save(dragAndDropMapping);
        incorrectDragAndDropMapping = dragAndDropMappingRepository.save(incorrectDragAndDropMapping);
        mappingWithImage = dragAndDropMappingRepository.save(mappingWithImage);
        dragAndDropMapping.setQuestion(dragAndDropQuestion);
        incorrectDragAndDropMapping.setQuestion(dragAndDropQuestion);
        mappingWithImage.setQuestion(dragAndDropQuestion);
        quizQuestionRepository.save(dragAndDropQuestion);
        submittedAnswerRepository.save(submittedAnswerMC);
        submittedAnswerRepository.save(submittedAnswerSC);
        submittedAnswerRepository.save(submittedShortAnswer);
        quizSubmission.addSubmittedAnswers(submittedAnswerMC);
        quizSubmission.addSubmittedAnswers(submittedShortAnswer);
        quizSubmission.addSubmittedAnswers(submittedDragAndDropAnswer);
        quizSubmission.addSubmittedAnswers(submittedAnswerSC);
        studentParticipation.addSubmission(quizSubmission);
        quizQuestionRepository.save(dragAndDropQuestion);
        quizExercise = quizExerciseRepository.save(quizExercise);
        studentParticipationRepository.save(studentParticipation);
        quizSubmissionRepository.save(quizSubmission);
        quizExercise.addParticipation(studentParticipation);
        courseRepo.save(course);
        quizExerciseRepository.save(quizExercise);
        return quizSubmission;
    }

    /**
     * Creates and saves a quiz exercise with all question types using the passed dates and adds it to the given course.
     * After initialization, the quiz consists of one multiple choice, one drag and drop, one short answer, and single choice question.
     *
     * @param releaseDate       The release date of the quiz.
     * @param dueDate           The due date of the quiz.
     * @param assessmentDueDate The assessment due date of the quiz.
     * @param quizMode          The quiz mode of the quiz.
     * @param course            The course to which the quiz should be added to.
     * @return The newly created quiz exercise.
     */
    public QuizExercise createAndSaveQuizWithAllQuestionTypes(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, QuizMode quizMode) {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, dueDate, assessmentDueDate, quizMode, course);
        QuizExerciseFactory.addAllQuestionTypesToQuizExercise(quizExercise);
        return quizExerciseRepository.save(quizExercise);
    }
}
