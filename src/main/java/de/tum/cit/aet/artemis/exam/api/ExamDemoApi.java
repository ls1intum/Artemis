package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Creates the test exam of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(ExamEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class ExamDemoApi extends AbstractExamApi {

    /**
     * Title of the demo test exam. Used as its idempotency key together with the course, so it must stay stable.
     */
    private static final String TEST_EXAM_TITLE = "Practice Exam: Software Engineering Fundamentals";

    private static final int WORKING_TIME_SECONDS = 60 * 60;

    private static final String EXAMINER = "Demo Instructor";

    private static final String MODULE_NUMBER = "SE-101";

    private static final String START_TEXT = """
            This practice exam lets you check under exam conditions what you have learned in the course. You have 60 minutes for its exercises.

            It does not count towards your grade, and you can take it as often as you like: every attempt starts from scratch.
            """;

    private static final String END_TEXT = """
            Well done! Your quiz answers are evaluated right away, so the summary of your attempt shows which ones were right. Start another attempt whenever you want to \
            practice again.
            """;

    private static final Logger log = LoggerFactory.getLogger(ExamDemoApi.class);

    private final ExamRepository examRepository;

    private final ChannelService channelService;

    public ExamDemoApi(ExamRepository examRepository, ChannelService channelService) {
        this.examRepository = examRepository;
        this.channelService = channelService;
    }

    /**
     * Creates the test exam of the demo course if it does not exist yet, with one mandatory exercise group per given creator, each holding the exercise its creator creates.
     * <p>
     * A test exam can be started by every student of the course without registering, as often as they like, so the demo student can try it at any time. It became visible two
     * weeks before seeding, started a week before seeding and ends a year after seeding, so that it stays open for the lifetime of a demo instance; every attempt has a working
     * time of 60 minutes. The dates are never revisited.
     * <p>
     * The exam is identified by its title. An exam that already exists is left as it is, together with its exercise groups and exercises, so the creators are only called when
     * the exam is created.
     *
     * @param course           the demo course the exam belongs to.
     * @param exerciseCreators the creators of the exam exercises by the title of their exercise group, in the order of the groups. Each creates one exercise in the group it
     *                             is given, which the exam module cannot do itself without depending on the modules of the exercises.
     */
    public void createDemo(Course course, SequencedMap<String, Function<ExerciseGroup, ? extends Exercise>> exerciseCreators) {
        if (examRepository.findByCourseId(course.getId()).stream().anyMatch(exam -> TEST_EXAM_TITLE.equals(exam.getTitle()))) {
            log.debug("Demo test exam '{}' already exists, skipping creation", TEST_EXAM_TITLE);
            return;
        }

        Exam exam = createTestExam(course);
        double maxPoints = 0;
        int numberOfExercises = 0;
        try {
            for (var groupTitleAndCreator : exerciseCreators.entrySet()) {
                ExerciseGroup exerciseGroup = createExerciseGroup(exam.getId(), groupTitleAndCreator.getKey());
                try {
                    maxPoints += groupTitleAndCreator.getValue().apply(exerciseGroup).getMaxPoints();
                    numberOfExercises++;
                }
                catch (RuntimeException exception) {
                    // A mandatory group without an exercise would keep students from starting the exam.
                    removeExerciseGroup(exam.getId(), exerciseGroup.getId());
                    throw exception;
                }
            }
        }
        finally {
            // Also if an exercise could not be created: seeding never revisits an existing exam, and students can only start an exam that states its number of exercises.
            completeExam(exam.getId(), (int) Math.round(maxPoints), numberOfExercises);
        }

        log.info("Created demo test exam '{}' with id {}", TEST_EXAM_TITLE, exam.getId());
    }

    /**
     * Creates the test exam like {@code ExamResource#createExam} for the request the exam editor of the client sends: the exam is saved and gets its channel. Unlike the
     * resource, it does not add the exam to the global search right away, which the global search reconciliation catches up on while it is enabled.
     */
    private Exam createTestExam(Course course) {
        ZonedDateTime now = ZonedDateTime.now();
        Exam exam = new Exam();
        exam.setTitle(TEST_EXAM_TITLE);
        exam.setTestExam(true);
        exam.setVisibleDate(now.minusWeeks(2));
        exam.setStartDate(now.minusWeeks(1));
        exam.setEndDate(now.plusYears(1));
        exam.setWorkingTime(WORKING_TIME_SECONDS);
        // Test exams are not corrected by tutors, see ExamResource#checkExamPointsAndCorrectionRoundsElseThrow.
        exam.setNumberOfCorrectionRoundsInExam(0);
        // The defaults of the exam editor: the actual points follow once the exercises exist, see completeExam.
        exam.setExamMaxPoints(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setStartText(START_TEXT);
        exam.setEndText(END_TEXT);
        exam.setExaminer(EXAMINER);
        exam.setModuleNumber(MODULE_NUMBER);
        // The exam editor proposes the title of the course for a new exam.
        exam.setCourseName(course.getTitle());
        exam.setCourse(course);

        Exam savedExam = examRepository.save(exam);
        channelService.createExamChannel(savedExam, Optional.empty());
        return savedExam;
    }

    /**
     * Creates a mandatory exercise group at the end of the exam like {@code ExerciseGroupResource#createExerciseGroup}: the group is saved as part of the exam, which sets its
     * position.
     */
    private ExerciseGroup createExerciseGroup(long examId, String title) {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle(title);
        Exam exam = examRepository.findByIdWithExerciseGroupsElseThrow(examId);
        exam.addExerciseGroup(exerciseGroup);
        return examRepository.save(exam).getExerciseGroups().getLast();
    }

    private void removeExerciseGroup(long examId, long exerciseGroupId) {
        Exam exam = examRepository.findByIdWithExerciseGroupsElseThrow(examId);
        exam.getExerciseGroups().stream().filter(exerciseGroup -> exerciseGroup.getId() == exerciseGroupId).findFirst().ifPresent(exam::removeExerciseGroup);
        examRepository.save(exam);
    }

    /**
     * Sets the points and the number of exercises of the exam to what its exercise groups hold, which is what the exam checklist asks the instructor to do through
     * {@code ExamResource#updateExam} once the exercises exist. A student exam can only be generated once the number of exercises is set.
     */
    private void completeExam(long examId, int maxPoints, int numberOfExercises) {
        Exam exam = examRepository.findByIdElseThrow(examId);
        exam.setExamMaxPoints(maxPoints);
        exam.setNumberOfExercisesInExam(numberOfExercises);
        examRepository.save(exam);
    }
}
