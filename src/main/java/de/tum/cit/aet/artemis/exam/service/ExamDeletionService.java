package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_EXERCISE_START_STATUS;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.ExamDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamLiveEventRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ExamDeletionService.class);

    private final ExerciseDeletionService exerciseDeletionService;

    private final ParticipationDeletionService participationDeletionService;

    private final CacheManager cacheManager;

    private final UserRepository userRepository;

    private final ExamRepository examRepository;

    private final AuditEventRepository auditEventRepository;

    private final StudentExamRepository studentExamRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ChannelRepository channelRepository;

    private final ChannelService channelService;

    private final ExamLiveEventRepository examLiveEventRepository;

    private final BuildJobRepository buildJobRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ExamUserRepository examUserRepository;

    public ExamDeletionService(ExerciseDeletionService exerciseDeletionService, ParticipationDeletionService participationDeletionService, CacheManager cacheManager,
            UserRepository userRepository, ExamRepository examRepository, AuditEventRepository auditEventRepository, StudentExamRepository studentExamRepository,
            GradingScaleRepository gradingScaleRepository, StudentParticipationRepository studentParticipationRepository, ChannelRepository channelRepository,
            ChannelService channelService, ExamLiveEventRepository examLiveEventRepository, BuildJobRepository buildJobRepository, PostRepository postRepository,
            AnswerPostRepository answerPostRepository, ProgrammingExerciseRepository programmingExerciseRepository, ExamUserRepository examUserRepository) {
        this.exerciseDeletionService = exerciseDeletionService;
        this.participationDeletionService = participationDeletionService;
        this.cacheManager = cacheManager;
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.auditEventRepository = auditEventRepository;
        this.studentExamRepository = studentExamRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.examLiveEventRepository = examLiveEventRepository;
        this.buildJobRepository = buildJobRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.examUserRepository = examUserRepository;
    }

    /**
     * Fetches the exam and eagerly loads all required elements and deletes all elements associated with the
     * exam including:
     * <ul>
     * <li>The Exam</li>
     * <li>All ExerciseGroups</li>
     * <li>All Exercises including:
     * Submissions, Participations, Results, Repositories and build plans, see {@link ExerciseDeletionService#delete}</li>
     * <li>All StudentExams</li>
     * <li>The exam Grading Scale if such exists</li>
     * </ul>
     * Note: StudentExams and ExerciseGroups are not explicitly deleted as the delete operation of the exam is cascaded by the database.
     *
     * @param examId the ID of the exam to be deleted
     */
    public void delete(@NotNull long examId) {
        User user = userRepository.getUser();
        Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        log.info("User {} has requested to delete the exam {}", user.getLogin(), exam.getTitle());
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);

        Channel examChannel = channelRepository.findChannelByExamId(examId);
        channelService.deleteChannel(examChannel);

        // first delete test runs to avoid issues later
        List<StudentExam> testRuns = studentExamRepository.findAllTestRunsByExamId(examId);
        testRuns.forEach(testRun -> deleteTestRun(testRun.getId()));

        // fetch the exam again to allow Hibernate to delete the exercises properly
        exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);

        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup != null) {
                for (Exercise exercise : exerciseGroup.getExercises()) {
                    exerciseDeletionService.delete(exercise.getId(), true);
                }
            }
        }

        deleteGradingScaleOfExam(exam);
        // fetch the exam again to allow Hibernate to delete it properly
        exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        examRepository.delete(exam);
    }

    private void deleteGradingScaleOfExam(Exam exam) {
        // delete exam grading scale if it exists
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamId(exam.getId());
        gradingScale.ifPresent(gradingScaleRepository::delete);
    }

    /**
     * Deletes all elements associated with the exam but not the exam itself in order to reset it.
     * <p>
     * The deleted elements are:
     * <ul>
     * <li>All StudentExams</li>
     * <li>Everything that has been submitted by students to the exercises that are part of the exam,
     * but not the exercises themself. See {@link ExerciseDeletionService#reset}</li>
     * </ul>
     *
     * @param examId the ID of the exam to be reset
     */
    public void reset(@NotNull Long examId) {
        User user = userRepository.getUser();
        Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        log.info("User {} has requested to reset the exam {}", user.getLogin(), exam.getTitle());
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.RESET_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup != null) {
                for (Exercise exercise : exerciseGroup.getExercises()) {
                    exerciseDeletionService.reset(exercise);
                }
            }
        }
        studentExamRepository.deleteAll(exam.getStudentExams());
        examLiveEventRepository.deleteAllByExamId(examId);

        var studentExamExercisePreparationCache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
        if (studentExamExercisePreparationCache != null) {
            studentExamExercisePreparationCache.evict(examId);
        }
    }

    /**
     * Deletes student exams and existing participations for an exam.
     *
     * @param examId the ID of the exam where the student exams and participations should be deleted
     */
    public void deleteStudentExamsAndExistingParticipationsForExam(@NotNull Long examId) {
        User user = userRepository.getUser();
        Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        log.info("User {} has requested to delete existing student exams and participations for exam {}", user.getLogin(), exam.getTitle());
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup != null) {
                for (Exercise exercise : exerciseGroup.getExercises()) {
                    exerciseDeletionService.deletePlagiarismResultsAndParticipations(exercise);
                }
            }
        }
        studentExamRepository.deleteAll(exam.getStudentExams());
    }

    /**
     * Deletes a test run.
     * In case the participation is not referenced by other test runs, the participation, submission, build plans and repositories are deleted as well.
     *
     * @param testRunId the id of the test run
     */
    public void deleteTestRun(Long testRunId) {
        var testRun = studentExamRepository.findByIdWithExercisesElseThrow(testRunId);
        User instructor = testRun.getUser();
        var participations = studentParticipationRepository.findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(instructor.getId(),
                testRun.getExercises());
        testRun.getExercises().forEach(exercise -> {
            var relevantParticipation = exercise.findParticipation(participations);
            if (relevantParticipation != null) {
                exercise.setStudentParticipations(Set.of(relevantParticipation));
            }
            else {
                exercise.setStudentParticipations(new HashSet<>());
            }
        });

        List<StudentExam> otherTestRunsOfInstructor = studentExamRepository.findAllTestRunsWithExercisesByExamIdForUser(testRun.getExam().getId(), instructor.getId()).stream()
                .filter(studentExam -> !studentExam.getId().equals(testRunId)).toList();

        // We cannot delete participations which are referenced by other test runs. (an instructor is free to create as many test runs as he likes)
        var testRunExercises = testRun.getExercises();
        // Collect all distinct exercises of other instructor test runs
        var allInstructorTestRunExercises = otherTestRunsOfInstructor.stream().flatMap(studentExam -> studentExam.getExercises().stream()).distinct().toList();
        // Collect exercises which are not referenced by other test runs. Their participations can be safely deleted
        var exercisesWithParticipationsToBeDeleted = testRunExercises.stream().filter(exercise -> !allInstructorTestRunExercises.contains(exercise)).toList();

        for (final Exercise exercise : exercisesWithParticipationsToBeDeleted) {
            // Only delete participations that exist (and were not deleted in some other way)
            if (!exercise.getStudentParticipations().isEmpty()) {
                participationDeletionService.delete(exercise.getStudentParticipations().iterator().next().getId(), true);
            }
        }

        // Delete the test run student exam
        log.info("Request to delete Test Run {}", testRunId);
        studentExamRepository.deleteById(testRunId);
    }

    /**
     * Get the exam deletion summary for the given exam.
     *
     * @param examId the ID of the exam for which the deletion summary should be fetched
     * @return the exam deletion summary
     */
    public ExamDeletionSummaryDTO getExamDeletionSummary(@NotNull long examId) {
        Set<Long> programmingExerciseIds = programmingExerciseRepository.findProgrammingExerciseIdsByExamId(examId);
        long numberOfBuilds = buildJobRepository.countBuildJobsByExerciseIds(programmingExerciseIds);

        Channel channel = channelRepository.findChannelByExamId(examId);
        long conversationId = channel.getId();

        long numberOfCommunicationPosts = postRepository.countByConversationId(conversationId);
        long numberOfAnswerPosts = answerPostRepository.countByConversationId(conversationId);

        long numberRegisteredStudents = examUserRepository.countByExamId(examId);
        long notStartedExams = studentExamRepository.countStudentExamsNotStartedByExamIdIgnoreTestRuns(examId);
        long startedExams = studentExamRepository.countStudentExamsStartedByExamIdIgnoreTestRuns(examId);
        long submittedExams = studentExamRepository.countStudentExamsSubmittedByExamIdIgnoreTestRuns(examId);

        return new ExamDeletionSummaryDTO(numberOfBuilds, numberOfCommunicationPosts, numberOfAnswerPosts, numberRegisteredStudents, notStartedExams, startedExams, submittedExams);
    }
}
