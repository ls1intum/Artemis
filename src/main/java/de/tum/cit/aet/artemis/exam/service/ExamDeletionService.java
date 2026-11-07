package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_EXERCISE_START_STATUS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamLiveEventRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamSessionRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.web.ExamWebsocketTopics;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.EntityDeletionProgressDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionInfoDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseParticipationCountDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.DeletionProgressListener;
import de.tum.cit.aet.artemis.exercise.service.DeletionStepReporter;
import de.tum.cit.aet.artemis.exercise.service.EntityDeletionProgressService;
import de.tum.cit.aet.artemis.exercise.service.EntityDeletionWeights;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

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

    private final ExamSessionRepository examSessionRepository;

    private final ExamUserRepository examUserRepository;

    private final ExamUserService examUserService;

    private final StudentExamService studentExamService;

    private final Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService;

    private final ParticipationRepository participationRepository;

    private final EntityDeletionProgressService entityDeletionProgressService;

    private final ExerciseGroupRepository exerciseGroupRepository;

    public ExamDeletionService(ExerciseDeletionService exerciseDeletionService, ParticipationDeletionService participationDeletionService, CacheManager cacheManager,
            UserRepository userRepository, ExamRepository examRepository, AuditEventRepository auditEventRepository, StudentExamRepository studentExamRepository,
            GradingScaleRepository gradingScaleRepository, StudentParticipationRepository studentParticipationRepository, ChannelRepository channelRepository,
            ChannelService channelService, ExamLiveEventRepository examLiveEventRepository, ExamSessionRepository examSessionRepository, ExamUserRepository examUserRepository,
            ExamUserService examUserService, final StudentExamService studentExamService, Optional<SearchableEntityWeaviateService> searchableEntityWeaviateServiceOptional,
            ParticipationRepository participationRepository, EntityDeletionProgressService entityDeletionProgressService, ExerciseGroupRepository exerciseGroupRepository) {
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
        this.examSessionRepository = examSessionRepository;
        this.examUserRepository = examUserRepository;
        this.examUserService = examUserService;
        this.studentExamService = studentExamService;
        this.searchableEntityWeaviateService = searchableEntityWeaviateServiceOptional;
        this.participationRepository = participationRepository;
        this.entityDeletionProgressService = entityDeletionProgressService;
        this.exerciseGroupRepository = exerciseGroupRepository;
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
    public void delete(long examId) {
        String userLogin = userRepository.getUser().getLogin();
        auditDeletion(examId, userLogin);
        deleteExam(examId, userLogin, DeletionStepReporter.none(), Map.of());
    }

    /**
     * Records who requested the deletion of the exam. The background deletion does this when it accepts the request, so the audit trail is complete even if the server stops
     * before the job runs.
     */
    private void auditDeletion(long examId, String userLogin) {
        String examTitle = examRepository.findTitleById(examId);
        log.info("User {} has requested to delete the exam {}", userLogin, examTitle);
        auditEventRepository.add(new AuditEvent(userLogin, Constants.DELETE_EXAM, "exam=" + examTitle));
    }

    /**
     * Deletes the exam in the background and reports the progress on the websocket topic of the exam. Returns as soon as the deletion is scheduled. The caller checks the
     * permissions first. Unlike {@link #delete(long)}, this also removes the signature and photo files of the registered students.
     *
     * @param courseId  the id of the course of the exam
     * @param examId    the id of the exam to delete
     * @param userLogin the login of the instructor who requested the deletion, for the audit log
     * @throws ConflictException if the exam, or one of its exercise groups, is already being deleted
     */
    public void startAsyncDeletion(long courseId, long examId, String userLogin) {
        List<ExerciseDeletionInfoDTO> exercises = exerciseGroupRepository.findExerciseDeletionInfoByExamId(examId);
        Map<Long, Long> participationCounts = countParticipations(exercises);
        Map<String, Double> stepWeights = EntityDeletionWeights.examStepWeights(studentExamRepository.countTestRunsByExamId(examId),
                exercisesWeight(exercises, participationCounts), examRepository.countStudentExamsByExamId(examId), examUserRepository.countByExamId(examId));
        // The exam also holds the scope of each of its exercises, so none of them can be deleted on its own meanwhile, and the request is refused if one of them is deleted now.
        entityDeletionProgressService.startAsync(EntityDeletionProgressService.examScopeKey(examId), exerciseScopeKeys(exercises), EntityDeletionType.EXAM, examId,
                ExamWebsocketTopics.DELETION_PROGRESS.at(courseId, examId), stepWeights.size(), EntityDeletionWeights.total(stepWeights), () -> auditDeletion(examId, userLogin),
                tracker -> {
                    var reporter = new DeletionStepReporter(tracker, stepWeights);
                    // The registrations disappear with the exam, but their files do not, so they have to be known before the deletion.
                    List<ExamUser> examUsers = examUserRepository.findAllByExamId(examId);
                    deleteExam(examId, userLogin, reporter, participationCounts);
                    reporter.stepStartedAfterDeletion(EntityDeletionWeights.STEP_CLEANUP);
                    deleteImagesOfExamUsers(examUsers, examId);
                    reporter.finishCurrentStep();
                });
    }

    /**
     * Removes the signature and photo files of the registrations of a deleted exam. The exam is gone at this point, so a file that cannot be removed is logged and does not turn
     * the deletion into a failed one.
     */
    private void deleteImagesOfExamUsers(List<ExamUser> examUsers, long examId) {
        for (ExamUser examUser : examUsers) {
            try {
                examUserService.deleteAvailableExamUserImages(examUser);
            }
            catch (RuntimeException e) {
                log.warn("Could not delete the image files of exam user {} of the deleted exam {}", examUser.getId(), examId, e);
            }
        }
    }

    /**
     * Deletes an exercise group with all its exercises in the background and reports the progress on the websocket topic of the exam. Returns as soon as the deletion is
     * scheduled. The caller checks the permissions first.
     *
     * @param courseId                  the id of the course of the exam
     * @param examId                    the id of the exam of the exercise group
     * @param exerciseGroupId           the id of the exercise group to delete
     * @param deleteBaseReposBuildPlans whether the template and solution repos and build plans of programming exercises should be deleted as well
     * @param onAccepted                runs in the request once the deletion is accepted and before it starts, for example to audit the request. It does not run if the exam, one
     *                                      of its exercise groups or one of the exercises of this group is already being deleted.
     * @throws ConflictException if the exam, or one of its exercise groups, or one of the exercises of this group is already being deleted
     */
    public void startAsyncExerciseGroupDeletion(long courseId, long examId, long exerciseGroupId, boolean deleteBaseReposBuildPlans, Runnable onAccepted) {
        List<ExerciseDeletionInfoDTO> exercises = exerciseGroupRepository.findExerciseDeletionInfoByExerciseGroupId(exerciseGroupId);
        Map<Long, Long> participationCounts = countParticipations(exercises);
        Map<String, Double> stepWeights = EntityDeletionWeights.exerciseGroupStepWeights(exercisesWeight(exercises, participationCounts));
        entityDeletionProgressService.startAsync(EntityDeletionProgressService.examScopeKey(examId), exerciseScopeKeys(exercises), EntityDeletionType.EXERCISE_GROUP,
                exerciseGroupId, ExamWebsocketTopics.DELETION_PROGRESS.at(courseId, examId), stepWeights.size(), EntityDeletionWeights.total(stepWeights), onAccepted, tracker -> {
                    var reporter = new DeletionStepReporter(tracker, stepWeights);
                    ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdWithExercisesElseThrow(exerciseGroupId);
                    deleteExercises(exerciseGroup.getExercises(), deleteBaseReposBuildPlans, reporter, participationCounts);

                    reporter.stepStarted(EntityDeletionWeights.STEP_EXERCISE_GROUP);
                    // Remove the exercise group by removing it from the list of exercise groups of the corresponding exam.
                    // This is necessary as @OrderColumn (exercise_group_order) needs continuous values. Otherwise the client will
                    // receive null values for the gaps in exam.getExerciseGroups().
                    Exam exam = examRepository.findByIdWithExerciseGroupsElseThrow(examId);
                    List<ExerciseGroup> filteredExerciseGroups = exam.getExerciseGroups();
                    filteredExerciseGroups.removeIf(exGroup -> exGroup.getId().equals(exerciseGroupId));
                    exam.setExerciseGroups(filteredExerciseGroups);
                    examRepository.save(exam);
                    reporter.finishCurrentStep();
                });
    }

    /**
     * Get the latest known progress of the background deletion of the exam or of one of its exercise groups.
     *
     * @param examId the id of the exam
     * @return the progress, or empty if neither the exam nor one of its exercise groups was deleted in the background during the last hour
     */
    public Optional<EntityDeletionProgressDTO> getDeletionProgress(long examId) {
        return entityDeletionProgressService.getProgress(EntityDeletionProgressService.examScopeKey(examId));
    }

    private List<String> exerciseScopeKeys(List<ExerciseDeletionInfoDTO> exercises) {
        return exercises.stream().map(exercise -> EntityDeletionProgressService.exerciseScopeKey(exercise.id())).toList();
    }

    /**
     * Counts the participations of all exercises with one query. An exercise without participations is not part of the result.
     */
    private Map<Long, Long> countParticipations(List<ExerciseDeletionInfoDTO> exercises) {
        if (exercises.isEmpty()) {
            return Map.of();
        }
        List<Long> exerciseIds = exercises.stream().map(ExerciseDeletionInfoDTO::id).toList();
        return participationRepository.countByExerciseIdsGroupedByExercise(exerciseIds).stream()
                .collect(Collectors.toMap(ExerciseParticipationCountDTO::exerciseId, ExerciseParticipationCountDTO::count));
    }

    private double exercisesWeight(List<ExerciseDeletionInfoDTO> exercises, Map<Long, Long> participationCounts) {
        return exercises.stream()
                .mapToDouble(exercise -> EntityDeletionWeights.exerciseWeight(exercise.isProgrammingExercise(), participationCounts.getOrDefault(exercise.id(), 0L))).sum();
    }

    private void deleteExam(long examId, String userLogin, DeletionStepReporter reporter, Map<Long, Long> participationCounts) {
        Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        log.info("Deleting the exam {} requested by {}", exam.getTitle(), userLogin);

        reporter.stepStarted(EntityDeletionWeights.STEP_TEST_RUNS);
        // The channel is already gone if an earlier deletion of this exam failed after this step, and the retry has to work as well.
        Channel examChannel = channelRepository.findChannelByExamId(examId);
        if (examChannel != null) {
            channelService.deleteChannel(examChannel);
        }

        // first delete test runs to avoid issues later
        List<StudentExam> testRuns = studentExamRepository.findAllTestRunsByExamId(examId);
        testRuns.forEach(testRun -> deleteTestRun(testRun.getId()));

        // fetch the exam again to allow Hibernate to delete the exercises properly
        exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);

        List<Exercise> exercises = new ArrayList<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup != null) {
                exercises.addAll(exerciseGroup.getExercises());
            }
        }
        deleteExercises(exercises, true, reporter, participationCounts);

        reporter.stepStarted(EntityDeletionWeights.STEP_GRADING_SCALE);
        deleteGradingScaleOfExam(exam);

        reporter.stepStarted(EntityDeletionWeights.STEP_EXAM);
        searchableEntityWeaviateService.ifPresent(service -> service.deleteEntityAsync(SearchableEntitySchema.TypeValues.EXAM, examId));

        // fetch the exam again to allow Hibernate to delete it properly
        exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        examRepository.delete(exam);
        reporter.finishCurrentStep();
    }

    /**
     * Deletes the exercises one after the other and reports how many are done, with the title of the one that is deleted now.
     */
    private void deleteExercises(Collection<Exercise> exercises, boolean deleteBaseReposBuildPlans, DeletionStepReporter reporter, Map<Long, Long> participationCounts) {
        reporter.stepStarted(EntityDeletionWeights.STEP_EXERCISES);
        int total = exercises.size();
        int deleted = 0;
        double completedWeight = 0;
        for (Exercise exercise : exercises) {
            // An exercise that joined the exam or group after the deletion was accepted is not claimed yet.
            reporter.claimExercise(exercise.getId());
            boolean programming = exercise instanceof ProgrammingExercise;
            long participations = participationCounts.getOrDefault(exercise.getId(), 0L);
            double participationsWeight = EntityDeletionWeights.participationsWeight(programming, participations);
            int deletedBefore = deleted;
            double completedBefore = completedWeight;
            String title = exercise.getTitle();
            reporter.exercisesProcessed(deletedBefore, total, title, completedBefore);
            exerciseDeletionService.delete(exercise.getId(), deleteBaseReposBuildPlans, new DeletionProgressListener() {

                @Override
                public void itemsProcessed(int processed, int totalParticipations) {
                    reporter.exercisesProcessed(deletedBefore, total, title, completedBefore + participationsWeight * processed / totalParticipations);
                }
            });
            deleted++;
            completedWeight += EntityDeletionWeights.exerciseWeight(programming, participations);
            reporter.exercisesProcessed(deleted, total, null, completedWeight);
        }
        reporter.finishCurrentStep();
    }

    private void deleteGradingScaleOfExam(Exam exam) {
        // delete exam grading scale if it exists
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamId(exam.getId());
        gradingScale.ifPresent(gradingScaleRepository::delete);
    }

    /**
     * Resets an exam by deleting all student data while preserving the exam structure.
     * <p>
     * This method is optimized to work with IDs only, avoiding expensive eager loading
     * of exercise groups and student exams. The exam title is fetched separately for
     * audit logging purposes.
     * <p>
     * The deleted elements are:
     * <ul>
     * <li>All ExamSessions (via bulk delete query, must be deleted before StudentExams due to foreign key constraint)</li>
     * <li>All StudentExams (via bulk delete query)</li>
     * <li>All student participations, submissions, and results for exam exercises</li>
     * <li>All plagiarism results for exam exercises</li>
     * <li>All exam live events</li>
     * <li>All ExamUsers, i.e. the registrations of the students with their seating and identity-check flags, so no student is registered
     * for the exam afterwards. The students themselves stay in the course. The signature and photo image files of the exam users are only
     * scheduled for deletion, on a best-effort basis, like in every other path that removes an exam user (unregistering one or all students
     * and deleting the exam).</li>
     * </ul>
     * <p>
     * The preserved elements are:
     * <ul>
     * <li>The exam configuration</li>
     * <li>All exercise groups and their exercises</li>
     * <li>Grading scale (if any)</li>
     * </ul>
     *
     * @param examId the ID of the exam to be reset
     */
    public void reset(@NonNull Long examId) {
        // Not userRepository.getUser(): the scheduled data-privacy cleanup runs this without a user principal, and
        // requiring one made the exam step of a course reset fail after other student data had already been deleted,
        // leaving a course that could never finish resetting. Audit the automated run as the system account instead.
        String userLogin = SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM_ACCOUNT);

        // Fetch only the title for audit logging - avoids loading the entire exam with exercises
        String examTitle = examRepository.findTitleById(examId);
        log.info("User {} has requested to reset the exam {}", userLogin, examTitle);
        AuditEvent auditEvent = new AuditEvent(userLogin, Constants.RESET_EXAM, "exam=" + examTitle);
        auditEventRepository.add(auditEvent);

        // Fetch exercise IDs directly - more efficient than loading full exercise entities
        Set<Long> exerciseIds = examRepository.findExerciseIdsByExamId(examId);

        // Reset each exercise by ID (deletes participations, submissions, results, plagiarism results)
        for (Long exerciseId : exerciseIds) {
            exerciseDeletionService.reset(exerciseId);
        }

        // Delete exam sessions first to avoid foreign key constraint violations
        // (exam_session references student_exam via student_exam_id)
        examSessionRepository.deleteAllByExamId(examId);

        // Delete all student exams via bulk query - more efficient than loading and deleting entities
        studentExamRepository.deleteAllByExamId(examId);

        // Delete exam live events
        examLiveEventRepository.deleteAllByExamId(examId);

        // Delete exam users (seating, identity-check flags, and the student signature/photo image files). These are not
        // cascade-removed on a reset because the exam entity itself is kept, so they must be deleted explicitly. The file
        // deletion is best-effort per user so a single malformed stored path cannot abort the whole reset.
        List<ExamUser> examUsers = examUserRepository.findAllByExamId(examId);
        for (ExamUser examUser : examUsers) {
            try {
                examUserService.deleteAvailableExamUserImages(examUser);
            }
            catch (Exception e) {
                log.warn("Could not schedule the image files of exam user {} (exam {}) for deletion", examUser.getId(), examId, e);
            }
        }
        examUserRepository.deleteAll(examUsers);

        // Clear the cache for student exam exercise preparation status
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
    public void deleteStudentExamsAndExistingParticipationsForExam(@NonNull Long examId) {
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
        studentExamService.invalidateExerciseStartStatus(examId);
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

        // We cannot delete participations which are referenced by other test runs. (an instructor is free to create as many test runs as they like)
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

    public void deleteByCourseId(long courseId) {
        var examIds = examRepository.findExamIdsByCourseId(courseId);
        for (var examId : examIds) {
            delete(examId);
        }
    }
}
