package de.tum.in.www1.artemis.exercise.quizexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TeamAssignmentConfig;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.QuizBatchJoinDTO;

/**
 * Service responsible for initializing the database with specific testdata related to quiz exercises for use in integration tests.
 */
@Service
public class QuizExerciseUtilService {

    private final Logger log = LoggerFactory.getLogger(getClass());

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
    private RequestUtilService requestUtilService;

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

    /**
     * Create, join and start a batch for student by tutor
     */
    public void prepareBatchForSubmitting(QuizExercise quizExercise, Authentication tutor, Authentication student) throws Exception {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        switch (quizExercise.getQuizMode()) {
            case SYNCHRONIZED -> {
            }
            case BATCHED -> {
                SecurityContextHolder.getContext().setAuthentication(tutor);
                var batch = requestUtilService.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/add-batch", null, QuizBatch.class, HttpStatus.OK);
                requestUtilService.put("/api/quiz-exercises/" + batch.getId() + "/start-batch", null, HttpStatus.OK);
                SecurityContextHolder.getContext().setAuthentication(student);
                requestUtilService.postWithoutLocation("/api/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(batch.getPassword()), HttpStatus.OK, null);
            }
            case INDIVIDUAL -> {
                SecurityContextHolder.getContext().setAuthentication(student);
                requestUtilService.postWithoutLocation("/api/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(null), HttpStatus.OK, null);
            }
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public Course addCourseWithOneQuizExercise() {
        return addCourseWithOneQuizExercise("Title");
    }

    public Course addCourseWithOneQuizExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = createQuiz(course, futureTimestamp, futureFutureTimestamp, QuizMode.SYNCHRONIZED);
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
     * Generates a submitted answer for a given question.
     *
     * @param question given question, the answer is for
     * @param correct  boolean whether the answer should be correct or not
     * @return created SubmittedAnswer
     */
    public SubmittedAnswer generateSubmittedAnswerFor(QuizQuestion question, boolean correct) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                if (answerOption.isIsCorrect().equals(correct)) {
                    submittedAnswer.addSelectedOptions(answerOption);
                }
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem1.toString());
            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem2.toString());
            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem3.toString());

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation1.toString());
            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation2.toString());
            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation3.toString());

            if (correct) {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3));
            }
            else {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation2));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation1));
            }

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (correct) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText(correctText.toUpperCase());
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    public SubmittedAnswer generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(QuizQuestion question) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                submittedAnswer.addSelectedOptions(answerOption);
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem1.toString());
            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem2.toString());
            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem3.toString());

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation1.toString());
            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation2.toString());
            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation3.toString());

            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation2));

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (spot.getSpotNr() == 2) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText("wrong submitted text");
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    @NotNull
    public QuizExercise createQuiz(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, dueDate, quizMode, course);
        initializeQuizExercise(quizExercise);
        return quizExercise;
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
        initializeQuizExercise(quizExercise);

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
        initializeQuizExercise(quizExercise);

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

    @NotNull
    public QuizExercise createQuizForExam(ExerciseGroup exerciseGroup) {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExerciseForExam(exerciseGroup);
        initializeQuizExercise(quizExercise);

        return quizExercise;
    }

    /**
     * initializes a quiz with all different types of questions
     *
     * @param quizExercise to be initialized
     */
    private void initializeQuizExercise(QuizExercise quizExercise) {
        quizExercise.addQuestions(createMultipleChoiceQuestion());
        quizExercise.addQuestions(createDragAndDropQuestion());
        quizExercise.addQuestions(createShortAnswerQuestion());
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.setGradingInstructions(null);
    }

    @NotNull
    public ShortAnswerQuestion createShortAnswerQuestion() {
        ShortAnswerQuestion sa = (ShortAnswerQuestion) new ShortAnswerQuestion().title("SA").score(2).text("This is a long answer text");
        sa.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        // TODO: we should test different values here
        sa.setMatchLetterCase(true);
        sa.setSimilarityValue(100);

        var shortAnswerSpot1 = new ShortAnswerSpot().spotNr(0).width(1);
        shortAnswerSpot1.setTempID(generateTempId());
        var shortAnswerSpot2 = new ShortAnswerSpot().spotNr(2).width(2);
        shortAnswerSpot2.setTempID(generateTempId());
        sa.getSpots().add(shortAnswerSpot1);
        sa.getSpots().add(shortAnswerSpot2);

        var shortAnswerSolution1 = new ShortAnswerSolution().text("is");
        shortAnswerSolution1.setTempID(generateTempId());
        var shortAnswerSolution2 = new ShortAnswerSolution().text("long");
        shortAnswerSolution2.setTempID(generateTempId());
        sa.addSolution(shortAnswerSolution1);
        // also invoke remove once
        sa.removeSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution2);

        var mapping1 = new ShortAnswerMapping().spot(sa.getSpots().get(0)).solution(sa.getSolutions().get(0));
        shortAnswerSolution1.addMappings(mapping1);
        shortAnswerSpot1.addMappings(mapping1);
        // also invoke remove once
        shortAnswerSolution1.removeMappings(mapping1);
        shortAnswerSpot1.removeMappings(mapping1);
        shortAnswerSolution1.addMappings(mapping1);
        shortAnswerSpot1.addMappings(mapping1);
        assertThat(shortAnswerSolution1.getMappings()).isNotEmpty();
        assertThat(shortAnswerSpot1.getMappings()).isNotEmpty();
        log.debug(shortAnswerSolution1.toString());
        log.debug(shortAnswerSpot1.toString());

        var mapping2 = new ShortAnswerMapping().spot(sa.getSpots().get(1)).solution(sa.getSolutions().get(1));
        sa.addCorrectMapping(mapping1);
        assertThat(sa).isEqualTo(mapping1.getQuestion());
        sa.removeCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping2);
        sa.setExplanation("Explanation");
        sa.setRandomizeOrder(true);
        // invoke some util methods
        log.debug("ShortAnswer: {}", sa);
        log.debug("ShortAnswer.hashCode: {}", sa.hashCode());
        sa.copyQuestionId();
        return sa;
    }

    @NotNull
    public DragAndDropQuestion createDragAndDropQuestion() {
        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(3).text("Q2");
        dnd.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);

        var dropLocation1 = new DropLocation().posX(10d).posY(10d).height(10d).width(10d);
        dropLocation1.setTempID(generateTempId());
        var dropLocation2 = new DropLocation().posX(20d).posY(20d).height(10d).width(10d);
        dropLocation2.setTempID(generateTempId());
        var dropLocation3 = new DropLocation().posX(30d).posY(30d).height(10d).width(10d);
        dropLocation3.setTempID(generateTempId());
        dnd.addDropLocation(dropLocation1);
        // also invoke remove once
        dnd.removeDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation2);
        dnd.addDropLocation(dropLocation3);

        var dragItem1 = new DragItem().text("D1");
        dragItem1.setTempID(generateTempId());
        var dragItem2 = new DragItem().text("D2");
        dragItem2.setTempID(generateTempId());
        var dragItem3 = new DragItem().text("D3");
        dragItem3.setTempID(generateTempId());
        dnd.addDragItem(dragItem1);
        assertThat(dragItem1.getQuestion()).isEqualTo(dnd);
        // also invoke remove once
        dnd.removeDragItem(dragItem1);
        dnd.addDragItem(dragItem1);
        dnd.addDragItem(dragItem2);
        dnd.addDragItem(dragItem3);

        var mapping1 = new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1);
        dragItem1.addMappings(mapping1);
        // also invoke remove
        dragItem1.removeMappings(mapping1);
        dragItem1.addMappings(mapping1);
        assertThat(dragItem1.getMappings()).isNotEmpty();

        dnd.addCorrectMapping(mapping1);
        dnd.removeCorrectMapping(mapping1);
        dnd.addCorrectMapping(mapping1);
        var mapping2 = new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2);
        dnd.addCorrectMapping(mapping2);
        var mapping3 = new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3);
        dnd.addCorrectMapping(mapping3);
        dnd.setExplanation("Explanation");
        // invoke some util methods
        log.debug("DnD: {}", dnd);
        log.debug("DnD.hashCode: {}", dnd.hashCode());
        dnd.copyQuestionId();

        return dnd;
    }

    public Long generateTempId() {
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    @NotNull
    public MultipleChoiceQuestion createMultipleChoiceQuestion() {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        mc.setScoringType(ScoringType.ALL_OR_NOTHING);
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        mc.setExplanation("Explanation");

        mc.copyQuestionId();
        return mc;
    }

    /**
     * Generate submissions for a student for an exercise. Results depend on the studentID.
     *
     * @param quizExercise   QuizExercise the submissions are for (we assume 3 questions here)
     * @param studentID      ID of the student
     * @param submitted      Boolean if it is submitted or not
     * @param submissionDate Submission date
     */
    public QuizSubmission generateSubmissionForThreeQuestions(QuizExercise quizExercise, int studentID, boolean submitted, ZonedDateTime submissionDate) {
        QuizSubmission quizSubmission = new QuizSubmission();
        QuizQuestion quizQuestion1 = quizExercise.getQuizQuestions().get(0);
        QuizQuestion quizQuestion2 = quizExercise.getQuizQuestions().get(1);
        QuizQuestion quizQuestion3 = quizExercise.getQuizQuestions().get(2);
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion1, studentID % 2 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion2, studentID % 3 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion3, studentID % 4 == 0));
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    /**
     * Generate a submission with all or none options of a MultipleChoiceQuestion selected, if there is one in the exercise
     *
     * @param quizExercise     Exercise the submission is for
     * @param submitted        Boolean whether it is submitted or not
     * @param submissionDate   Submission date
     * @param selectEverything Boolean whether every answer option should be selected or none
     */
    public QuizSubmission generateSpecialSubmissionWithResult(QuizExercise quizExercise, boolean submitted, ZonedDateTime submissionDate, boolean selectEverything) {
        QuizSubmission quizSubmission = new QuizSubmission();

        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.setQuizQuestion(question);
                if (selectEverything) {
                    for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                        submittedAnswer.addSelectedOptions(answerOption);
                    }
                }
                quizSubmission.addSubmittedAnswers(submittedAnswer);

            }
            else {
                quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(question, false));
            }
        }
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    public QuizExercise addQuizExerciseToCourseWithParticipationAndSubmissionForUser(Course course, String login) throws IOException {
        QuizExercise quizExercise = createAndSaveQuizWithAllQuestionTypes(course, futureTimestamp, futureFutureTimestamp, QuizMode.SYNCHRONIZED);
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
        var backgroundPathInFileSystem = Path.of(FilePathService.getDragAndDropBackgroundFilePath(), "drag_and_drop_background.jpg");
        var dragItemPathInFileSystem = Path.of(FilePathService.getDragItemFilePath(), "drag_item.jpg");
        if (Files.exists(backgroundPathInFileSystem)) {
            Files.delete(backgroundPathInFileSystem);
        }
        if (Files.exists(dragItemPathInFileSystem)) {
            Files.delete(dragItemPathInFileSystem);
        }
        Files.copy(new ClassPathResource("test-data/data-export/drag_and_drop_background.jpg").getInputStream(), backgroundPathInFileSystem);
        Files.copy(new ClassPathResource("test-data/data-export/drag_item.jpg").getInputStream(), dragItemPathInFileSystem);
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
        submittedAnswerRepository.save(submittedShortAnswer);
        quizSubmission.addSubmittedAnswers(submittedAnswerMC);
        quizSubmission.addSubmittedAnswers(submittedShortAnswer);
        quizSubmission.addSubmittedAnswers(submittedDragAndDropAnswer);
        studentParticipation.addSubmission(quizSubmission);
        quizQuestionRepository.save(dragAndDropQuestion);
        quizExercise = quizExerciseRepository.save(quizExercise);
        studentParticipationRepository.save(studentParticipation);
        quizSubmissionRepository.save(quizSubmission);
        quizExercise.addParticipation(studentParticipation);
        courseRepo.save(course);
        quizExerciseRepository.save(quizExercise);
        return quizExercise;
    }

    public QuizExercise createAndSaveQuizWithAllQuestionTypes(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, dueDate, quizMode, course);
        initializeQuizExerciseWithAllQuestionTypes(quizExercise);
        return quizExerciseRepository.save(quizExercise);
    }

    private void initializeQuizExerciseWithAllQuestionTypes(QuizExercise quizExercise) {
        quizExercise.addQuestions(createMultipleChoiceQuestionWithAllTypesOfAnswerOptions());
        quizExercise.addQuestions(createDragAndDropQuestionWithAllTypesOfMappings());
        quizExercise.addQuestions(createShortAnswerQuestionWithRealisticText());
        quizExercise.addQuestions(createSingleChoiceQuestion());
    }

    private ShortAnswerQuestion createShortAnswerQuestionWithRealisticText() {
        var shortAnswerQuestion = createShortAnswerQuestion();
        shortAnswerQuestion.setText("This [-spot1] a [-spot 2] answer text");
        return shortAnswerQuestion;
    }

    public DragAndDropQuestion createDragAndDropQuestionWithAllTypesOfMappings() {
        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(3).text("Q2");
        dnd.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);

        var dropLocation1 = new DropLocation().posX(10d).posY(10d).height(10d).width(10d);
        dropLocation1.setTempID(generateTempId());
        var dropLocation2 = new DropLocation().posX(20d).posY(20d).height(10d).width(10d);
        dropLocation2.setTempID(generateTempId());
        var dropLocation3 = new DropLocation().posX(30d).posY(30d).height(10d).width(10d);
        dropLocation3.setInvalid(true);
        dropLocation3.setTempID(generateTempId());
        var dropLocation4 = new DropLocation().posX(40d).posY(40d).height(10d).width(10d);
        dropLocation4.setTempID(generateTempId());
        var dropLocation5 = new DropLocation().posX(50d).posY(50d).height(10d).width(10d);
        dropLocation5.setTempID(generateTempId());
        dnd.addDropLocation(dropLocation1);
        // also invoke remove once
        dnd.removeDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation2);
        dnd.addDropLocation(dropLocation3);
        dnd.addDropLocation(dropLocation4);
        dnd.addDropLocation(dropLocation5);

        var dragItem1 = new DragItem().text("D1");
        dragItem1.setTempID(generateTempId());
        var dragItem2 = new DragItem().text("D2");
        dragItem2.setTempID(generateTempId());
        var dragItem3 = new DragItem().text("D3");
        dragItem3.setTempID(generateTempId());
        var dragItem4 = new DragItem().text("invalid drag item");
        dragItem4.setTempID(generateTempId());
        var dragItem5 = new DragItem().pictureFilePath("/api/files/drag-and-drop/drag-items/10/drag_item.jpg");
        dragItem4.setInvalid(true);
        dnd.addDragItem(dragItem1);
        assertThat(dragItem1.getQuestion()).isEqualTo(dnd);
        // also invoke remove once
        dnd.removeDragItem(dragItem1);
        dnd.addDragItem(dragItem1);
        dnd.addDragItem(dragItem2);
        dnd.addDragItem(dragItem3);
        dnd.addDragItem(dragItem4);
        dnd.addDragItem(dragItem5);

        var mapping1 = new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1);
        dragItem1.addMappings(mapping1);
        // also invoke remove
        dragItem1.removeMappings(mapping1);
        dragItem1.addMappings(mapping1);
        assertThat(dragItem1.getMappings()).isNotEmpty();

        dnd.addCorrectMapping(mapping1);
        dnd.removeCorrectMapping(mapping1);
        dnd.addCorrectMapping(mapping1);
        var mapping2 = new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2);
        dnd.addCorrectMapping(mapping2);
        var mapping3 = new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3);
        dnd.addCorrectMapping(mapping3);
        var mapping4 = new DragAndDropMapping().dragItem(dragItem4).dropLocation(dropLocation4);
        dnd.addCorrectMapping(mapping4);
        var mapping5 = new DragAndDropMapping().dragItem(dragItem5).dropLocation(dropLocation5);
        dnd.addCorrectMapping(mapping5);
        dnd.setExplanation("Explanation");
        return dnd;
    }

    @NotNull
    public MultipleChoiceQuestion createMultipleChoiceQuestionWithAllTypesOfAnswerOptions() {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        mc.setScoringType(ScoringType.ALL_OR_NOTHING);
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isInvalid(true).isCorrect(false));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("E").hint("H5").explanation("E5").isCorrect(false));
        return mc;
    }

    @NotNull
    public MultipleChoiceQuestion createSingleChoiceQuestion() {
        var singleChoiceQuestion = createMultipleChoiceQuestion();
        singleChoiceQuestion.setSingleChoice(true);
        return singleChoiceQuestion;
    }

}
