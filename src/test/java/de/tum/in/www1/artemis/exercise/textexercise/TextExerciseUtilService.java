package de.tum.in.www1.artemis.exercise.textexercise;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to text exercises for use in integration tests.
 */
@Service
public class TextExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private TextSubmissionRepository textSubmissionRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private FeedbackRepository feedbackRepo;

    @Autowired
    private TextBlockRepository textBlockRepo;

    @Autowired
    private PlagiarismResultRepository plagiarismResultRepo;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    /**
     * Generate a set of specified size containing TextBlocks with dummy Text
     *
     * @param count expected size of TextBlock set
     * @return Set of dummy TextBlocks
     */
    public Set<TextBlock> generateTextBlocks(int count) {
        Set<TextBlock> textBlocks = new HashSet<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    /**
     * Generate a set of specified size containing TextBlocks with the same text
     *
     * @param count expected size of TextBlock set
     * @return Set of TextBlocks with identical texts
     */
    public Set<TextBlock> generateTextBlocksWithIdenticalTexts(int count) {
        Set<TextBlock> textBlocks = new HashSet<>();
        TextBlock textBlock;
        String text = "TextBlock";

        for (int i = 0; i < count; i++) {
            String blockId = sha1Hex("id" + i + text);
            textBlock = new TextBlock();
            textBlock.setText(text);
            textBlock.setId(blockId);
            textBlock.automatic();
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    /**
     * Create n TextClusters and assign TextBlocks to new clusters.
     *
     * @param textBlocks   TextBlocks to fake cluster
     * @param clusterSizes Number of new clusters
     * @param textExercise TextExercise
     * @return List of TextClusters with assigned TextBlocks
     */
    public List<TextCluster> addTextBlocksToCluster(Set<TextBlock> textBlocks, int[] clusterSizes, TextExercise textExercise) {
        if (Arrays.stream(clusterSizes).sum() != textBlocks.size()) {
            throw new IllegalArgumentException("The clusterSizes sum has to be equal to the number of textBlocks");
        }

        List<TextCluster> clusters = createClustersForExercise(clusterSizes, textExercise);

        // Add all textblocks to a cluster
        int clusterIndex = 0;
        for (var textBlock : textBlocks) {
            // as long as cluster is full select another cluster
            do {
                clusterIndex = (clusterIndex + 1) % clusterSizes.length;
            }
            while (clusterSizes[clusterIndex] == 0);

            clusterSizes[clusterIndex]--;
            clusters.get(clusterIndex).addBlocks(textBlock);
        }

        return clusters;
    }

    private List<TextCluster> createClustersForExercise(int[] clusterSizes, TextExercise textExercise) {
        List<TextCluster> clusters = new ArrayList<>();
        for (int i = 0; i < clusterSizes.length; i++) {
            clusters.add(new TextCluster().exercise(textExercise));
        }
        return clusters;
    }

    public TextExercise createSampleTextExerciseWithSubmissions(Course course, List<TextBlock> textBlocks, int submissionCount, int submissionSize) {
        if (textBlocks.size() != submissionCount * submissionSize) {
            throw new IllegalArgumentException("number of textBlocks must be eqaul to submissionCount * submissionSize");
        }
        TextExercise textExercise = new TextExercise();
        textExercise.setCourse(course);
        textExercise.setTitle("Title");
        textExercise.setShortName("Shortname");
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        textExercise = textExerciseRepository.save(textExercise);

        // submissions.length must be equal to studentParticipations.length;
        for (int i = 0; i < submissionCount; i++) {
            TextSubmission submission = new TextSubmission();
            StudentParticipation studentParticipation = new StudentParticipation();
            studentParticipation.setParticipant(userRepository.getUser());
            studentParticipation.setExercise(textExercise);
            studentParticipation = participationRepository.save(studentParticipation);

            submission.setParticipation(studentParticipation);
            submission.setLanguage(Language.ENGLISH);
            submission.setText("Test123");
            submission.setBlocks(new HashSet<>(textBlocks.subList(i * submissionSize, (i + 1) * submissionSize)));
            submission.setSubmitted(true);
            submission.setSubmissionDate(ZonedDateTime.now());
            textBlocks.subList(i * submissionSize, (i + 1) * submissionSize).forEach(textBlock -> textBlock.setSubmission(submission));

            studentParticipation.addSubmission(submission);
            textSubmissionRepo.save(submission);

            textExercise.getStudentParticipations().add(studentParticipation);
        }
        return textExercise;
    }

    public TextExercise createIndividualTextExercise(Course course, ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        return exerciseRepo.save(textExercise);
    }

    public TextExercise createTeamTextExercise(Course course, ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        TextExercise teamTextExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        teamTextExercise.setMaxPoints(10.0);
        teamTextExercise.setBonusPoints(0.0);
        teamTextExercise.setMode(ExerciseMode.TEAM);
        return exerciseRepo.save(teamTextExercise);
    }

    /**
     * @param title The title of the to be added text exercise
     * @return A course with one specified text exercise
     */
    public Course addCourseWithOneReleasedTextExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle(title);
        course.addExercises(textExercise);
        course = courseRepo.save(course);
        textExercise = exerciseRepo.save(textExercise);
        assertThat(courseRepo.findWithEagerExercisesById(course.getId()).getExercises()).as("course contains the exercise").contains(textExercise);
        assertThat(textExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return course;
    }

    public Course addCourseWithOneReleasedTextExercise() {
        return addCourseWithOneReleasedTextExercise("Text");
    }

    public TextExercise addCourseExamExerciseGroupWithOneTextExercise(String title) {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        if (title != null) {
            textExercise.setTitle(title);
        }
        return exerciseRepo.save(textExercise);
    }

    public TextExercise addCourseExamExerciseGroupWithOneTextExercise() {
        return addCourseExamExerciseGroupWithOneTextExercise(null);
    }

    public TextExercise addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise() {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamWithReviewDatesAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        return exerciseRepo.save(textExercise);
    }

    public TextSubmission saveTextSubmission(TextExercise exercise, TextSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    private TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String studentLogin, Long teamId, String assessorLogin) {
        StudentParticipation participation = Optional.ofNullable(studentLogin).map(login -> participationUtilService.createAndSaveParticipationForExercise(exercise, login))
                .orElseGet(() -> participationUtilService.addTeamParticipationForExercise(exercise, teamId));

        submissionRepository.save(submission);

        participation.addSubmission(submission);
        Result result = new Result();
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setScore(100D);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = textSubmissionRepo.save(submission);
        resultRepo.save(result);
        studentParticipationRepo.save(participation);

        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    public TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String login, String assessorLogin) {
        return saveTextSubmissionWithResultAndAssessor(exercise, submission, login, null, assessorLogin);
    }

    public void saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, long teamId, String assessorLogin) {
        saveTextSubmissionWithResultAndAssessor(exercise, submission, null, teamId, assessorLogin);
    }

    public TextSubmission addTextSubmissionWithResultAndAssessorAndFeedbacks(TextExercise exercise, TextSubmission submission, String studentLogin, String assessorLogin,
            List<Feedback> feedbacks) {
        submission = saveTextSubmissionWithResultAndAssessor(exercise, submission, studentLogin, null, assessorLogin);
        Result result = submission.getLatestResult();
        for (Feedback feedback : feedbacks) {
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 1) save the child entity (without connection to the parent entity) and make sure to re-assign the return value
            feedback = feedbackRepo.save(feedback);
            // this also invokes feedback.setResult(result)
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 2) connect child and parent entity
            result.addFeedback(feedback);
        }
        // this automatically saves the feedback because of the CascadeType.All annotation
        // Important note to prevent 'JpaSystemException: null index column for collection':
        // 3) save the parent entity and make sure to re-assign the return value
        resultRepo.save(result);

        return submission;
    }

    public TextSubmission addAndSaveTextBlocksToTextSubmission(Set<TextBlock> blocks, TextSubmission submission) {
        blocks.forEach(block -> {
            block.setSubmission(submission);
            block.setTextFromSubmission();
            block.computeId();
        });
        submission.setBlocks(blocks);
        textBlockRepo.saveAll(blocks);
        return textSubmissionRepo.save(submission);
    }

    public TextSubmission createSubmissionForTextExercise(TextExercise textExercise, Participant participant, String text) {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission(text, Language.ENGLISH, true);
        textSubmission = textSubmissionRepo.save(textSubmission);

        StudentParticipation studentParticipation;
        if (participant instanceof User user) {
            studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, user);
        }
        else if (participant instanceof Team team) {
            studentParticipation = participationUtilService.addTeamParticipationForExercise(textExercise, team.getId());
        }
        else {
            throw new RuntimeException("Unsupported participant!");
        }
        studentParticipation.addSubmission(textSubmission);

        studentParticipationRepo.save(studentParticipation);
        textSubmissionRepo.save(textSubmission);
        return textSubmission;
    }

    public TextPlagiarismResult createTextPlagiarismResultForExercise(Exercise exercise) {
        TextPlagiarismResult result = new TextPlagiarismResult();
        result.setExercise(exercise);
        result.setSimilarityDistribution(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        result.setDuration(4);
        return plagiarismResultRepo.save(result);
    }

    public TextAssessmentEvent createSingleTextAssessmentEvent(Long courseId, Long userId, Long exerciseId, Long participationId, Long submissionId) {
        return TextExerciseFactory.generateTextAssessmentEvent(TextAssessmentEventType.VIEW_AUTOMATIC_SUGGESTION_ORIGIN, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId,
                userId, exerciseId, participationId, submissionId);
    }

    public Course addCourseWithOneFinishedTextExercise() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        TextExercise finishedTextExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, pastTimestamp.plusHours(12), pastTimestamp.plusHours(24), course);
        finishedTextExercise.setTitle("Finished");
        course.addExercises(finishedTextExercise);
        course = courseRepo.save(course);
        exerciseRepo.save(finishedTextExercise);
        return course;
    }
}
