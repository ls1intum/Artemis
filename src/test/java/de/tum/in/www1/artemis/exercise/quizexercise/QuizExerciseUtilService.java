package de.tum.in.www1.artemis.exercise.quizexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to quiz exercises for use in integration tests.
 */
@Service
public class QuizExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

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
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private QuizScheduleService quizScheduleService;

    public Course addCourseWithOneQuizExercise() {
        return addCourseWithOneQuizExercise("Title");
    }

    public Course addCourseWithOneQuizExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, futureTimestamp, futureFutureTimestamp, QuizMode.SYNCHRONIZED);
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

    public QuizSubmission saveQuizSubmission(QuizExercise exercise, QuizSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = quizSubmissionRepository.save(submission);
        return submission;
    }

    /**
     * important quiz fields are emptied, so it can be imported,
     *
     * @param quizExercise to be emptied
     */
    public void emptyOutQuizExercise(QuizExercise quizExercise) {
        quizExercise.setReleaseDate(null);
        quizExercise.setCourse(null);
        quizExercise.setDueDate(null);
        quizExercise.setAssessmentDueDate(null);
        quizExercise.setQuizBatches(new HashSet<>());
    }

    /**
     * Creates a new quiz that gets saved in the QuizExercise repository.
     *
     * @param releaseDate release date of the quiz, is also used to set the start date of the course
     * @param dueDate     due date of the quiz, is also used to set the end date of the course
     * @param quizMode    SYNCHRONIZED, BATCHED or INDIVIDUAL
     * @return quiz that was created
     */
    public QuizExercise createAndSaveQuiz(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        QuizExercise quizExercise = createQuiz(releaseDate, dueDate, quizMode);
        quizExerciseRepository.save(quizExercise);

        return quizExercise;
    }

    /**
     * Creates a new quiz
     *
     * @param releaseDate release date of the quiz, is also used to set the start date of the course
     * @param dueDate     due date of the quiz, is also used to set the end date of the course
     * @param quizMode    SYNCHRONIZED, BATCHED or INDIVIDUAL
     * @return quiz that was created
     */
    public QuizExercise createQuiz(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        Course course = courseUtilService.createAndSaveCourse(null, releaseDate == null ? null : releaseDate.minusDays(1), dueDate == null ? null : dueDate.plusDays(1), Set.of());

        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, dueDate, quizMode, course);
        QuizExerciseFactory.initializeQuizExercise(quizExercise);

        return quizExercise;
    }

    /**
     * Creates a team quiz exercise with a team and saves it into the repository.
     *
     * @param releaseDate release date of the quiz
     * @param dueDate     due date of the quiz
     * @param quizMode    SYNCHRONIZED, BATCHED or INDIVIDUAL
     * @param minTeamSize minimum number of members the team is allowed to have
     * @param maxTeamSize maximum number of members the team is allowed to have
     * @return exercise created
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
     * sets up a team quiz exercise.
     *
     * @param quiz        quiz exercise that should be a team exercise.
     * @param minTeamSize minimum number of members the team is allowed to have
     * @param maxTeamSize maximum number of members the team is allowed to have
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
     * Creates a new exam quiz that gets saved in the QuizExercise repository.
     *
     * @param startDate start date of the exam, is also used to set the end date of the course the exam is in
     * @param endDate   end date of the exam, is also used to set the end date of the course the exam is in
     * @return exam quiz that was created
     */
    @NotNull
    public QuizExercise createAndSaveExamQuiz(ZonedDateTime startDate, ZonedDateTime endDate) {
        Course course = courseUtilService.createAndSaveCourse(null, startDate.minusDays(1), endDate.plusDays(1), new HashSet<>());

        Exam exam = ExamFactory.generateExam(course, startDate.minusMinutes(5), startDate, endDate, false);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam);
        examRepository.save(exam);

        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExerciseForExam(exerciseGroup);
        QuizExerciseFactory.initializeQuizExercise(quizExercise);

        quizExerciseRepository.save(quizExercise);

        return quizExercise;
    }

    /**
     * renames the quiz with the passed title, the quiz gets saved in the repository.
     *
     * @param quizExercise quiz to be renamed
     * @param newTitle     new name of the quiz
     */
    public void renameAndSaveQuiz(QuizExercise quizExercise, String newTitle) {
        quizExercise.setTitle(newTitle);
        quizExerciseRepository.save(quizExercise);
    }

    /**
     * sets the quiz exercise of quiz batch and saves the batch into the repository
     *
     * @param batch        quiz batch that should get saved
     * @param quizExercise quiz exercise to be added to the batch
     */
    public void setQuizBatchExerciseAndSave(QuizBatch batch, QuizExercise quizExercise) {
        batch.setQuizExercise(quizExercise);
        quizBatchRepository.save(batch);
    }

    public QuizSubmission addQuizExerciseToCourseWithParticipationAndSubmissionForUser(Course course, String login, boolean dueDateInTheFuture) throws IOException {
        QuizExercise quizExercise;
        if (dueDateInTheFuture) {
            quizExercise = createAndSaveQuizWithAllQuestionTypes(course, pastTimestamp, futureTimestamp, futureTimestamp, QuizMode.SYNCHRONIZED);
        }
        else {
            quizExercise = createAndSaveQuizWithAllQuestionTypes(course, pastTimestamp, pastTimestamp, pastTimestamp, QuizMode.SYNCHRONIZED);
        }
        quizExercise.setTitle("quiz");
        quizExercise.setDuration(120);
        assertThat(quizExercise.getQuizQuestions()).isNotEmpty();
        assertThat(quizExercise.isValid()).isTrue();
        course.addExercises(quizExercise);
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setExercise(quizExercise);
        studentParticipation.setParticipant(userUtilService.getUserByLogin(login));
        QuizSubmission quizSubmission = new QuizSubmission();
        quizSubmission.setParticipation(studentParticipation);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.setScoreInPoints(2.0);
        var submittedAnswerMC = new MultipleChoiceSubmittedAnswer();
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) (quizExercise.getQuizQuestions().get(0));
        submittedAnswerMC.setSelectedOptions(Set.of(multipleChoiceQuestion.getAnswerOptions().get(0), multipleChoiceQuestion.getAnswerOptions().get(1)));
        submittedAnswerMC.setQuizQuestion(multipleChoiceQuestion);
        var submittedAnswerSC = new MultipleChoiceSubmittedAnswer();
        MultipleChoiceQuestion singleChoiceQuestion = (MultipleChoiceQuestion) (quizExercise.getQuizQuestions().get(3));
        submittedAnswerSC.setSelectedOptions(Set.of(multipleChoiceQuestion.getAnswerOptions().get(0)));
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
        shortAnswerSubmittedText2.setSpot(shortAnswerQuestion.getSpots().get(0));

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
        dragAndDropMapping.setDragItem(dragAndDropQuestion.getDragItems().get(0));
        dragAndDropMapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(0));
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

    public QuizExercise createAndSaveQuizWithAllQuestionTypes(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, QuizMode quizMode) {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, dueDate, assessmentDueDate, quizMode, course);
        QuizExerciseFactory.initializeQuizExerciseWithAllQuestionTypes(quizExercise);
        return quizExerciseRepository.save(quizExercise);
    }

    public void joinQuizBatch(QuizExercise quizExercise, QuizBatch batch, String username) {
        var user = new User();
        user.setLogin(username);
        quizScheduleService.joinQuizBatch(quizExercise, batch, user);
    }
}
