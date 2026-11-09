package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.ExampleSubmissionService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.EntityDeletionProgressDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionCountsDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.iris.api.IrisChatSessionApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismResultApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.text.api.TextApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Service Implementation for managing Exercise.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseDeletionService.class);

    private final ParticipationDeletionService participationDeletionService;

    private final ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    private final QuizExerciseService quizExerciseService;

    private final ExampleSubmissionService exampleSubmissionService;

    private final Optional<StudentExamApi> studentExamApi;

    private final ExerciseRepository exerciseRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final Optional<LectureUnitApi> lectureUnitApi;

    private final Optional<PlagiarismResultApi> plagiarismResultApi;

    private final Optional<TextApi> textApi;

    private final ChannelService channelService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<SearchableEntityWeaviateService> searchableItemWeaviateService;

    private final Optional<IrisChatSessionApi> irisChatSessionApi;

    private final EntityDeletionProgressService entityDeletionProgressService;

    private final ParticipationRepository participationRepository;

    private final TeamRepository teamRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    public ExerciseDeletionService(ExerciseRepository exerciseRepository, ParticipationDeletionService participationDeletionService,
            ProgrammingExerciseDeletionService programmingExerciseDeletionService, QuizExerciseService quizExerciseService,
            TutorParticipationRepository tutorParticipationRepository, ExampleSubmissionService exampleSubmissionService, Optional<StudentExamApi> studentExamApi,
            Optional<LectureUnitApi> lectureUnitApi, Optional<PlagiarismResultApi> plagiarismResultApi, Optional<TextApi> textApi, ChannelService channelService,
            Optional<CompetencyProgressApi> competencyProgressApi, Optional<SearchableEntityWeaviateService> searchableItemWeaviateService,
            Optional<IrisChatSessionApi> irisChatSessionApi, EntityDeletionProgressService entityDeletionProgressService, ParticipationRepository participationRepository,
            TeamRepository teamRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository) {
        this.exerciseRepository = exerciseRepository;
        this.participationDeletionService = participationDeletionService;
        this.programmingExerciseDeletionService = programmingExerciseDeletionService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleSubmissionService = exampleSubmissionService;
        this.quizExerciseService = quizExerciseService;
        this.studentExamApi = studentExamApi;
        this.lectureUnitApi = lectureUnitApi;
        this.plagiarismResultApi = plagiarismResultApi;
        this.textApi = textApi;
        this.channelService = channelService;
        this.competencyProgressApi = competencyProgressApi;
        this.searchableItemWeaviateService = searchableItemWeaviateService;
        this.irisChatSessionApi = irisChatSessionApi;
        this.entityDeletionProgressService = entityDeletionProgressService;
        this.participationRepository = participationRepository;
        this.teamRepository = teamRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Get a summary for the deletion of an exercise.
     *
     * @param exerciseId the id of the exercise
     * @return the summary of the deletion of the exercise
     */
    public ExerciseDeletionSummaryDTO getDeletionSummary(long exerciseId) {
        ExerciseDeletionCountsDTO counts = exerciseRepository.findDeletionCountsByExerciseId(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
        long numberOfTeams = teamRepository.getNumberOfTeamsForExercise(exerciseId);
        long numberOfSubmissions = submissionRepository.countByExerciseId(exerciseId);
        long numberOfResults = resultRepository.countByExerciseIds(Set.of(exerciseId));
        return new ExerciseDeletionSummaryDTO(counts.numberOfStudentParticipations(), numberOfTeams, numberOfSubmissions, numberOfResults, counts.numberOfAssessments(),
                counts.numberOfComplaints(), counts.numberOfPlagiarismComparisons(), counts.numberOfExampleSubmissions(), counts.numberOfBuilds(),
                counts.numberOfCommunicationPosts(), counts.numberOfAnswerPosts(), counts.numberOfCompetencyLinks(), counts.numberOfLectureUnitLinks(),
                counts.numberOfStudentExams());
    }

    /**
     * Get the latest known progress of the background deletion of an exercise.
     *
     * @param exerciseId the id of the exercise
     * @return the progress, or empty if the exercise was not deleted in the background during the last hour
     */
    public Optional<EntityDeletionProgressDTO> getDeletionProgress(long exerciseId) {
        return entityDeletionProgressService.getProgress(EntityDeletionProgressService.exerciseScopeKey(exerciseId));
    }

    /**
     * Finds the course of an exercise that was deleted in the background during the last hour, to authorize reading the progress of its deletion.
     *
     * @param exerciseId the id of the deleted exercise
     * @return the id of the course, or empty if no deletion of the exercise ran in the last hour
     */
    public Optional<Long> findCourseIdOfDeletion(long exerciseId) {
        return entityDeletionProgressService.findCourseId(EntityDeletionProgressService.exerciseScopeKey(exerciseId));
    }

    /**
     * Delete student build plans (except BASE/SOLUTION) and git repositories of all exercise student participations.
     *
     * @param exerciseId programming exercise for which build plans in respective student participations are deleted
     */
    public void cleanup(Long exerciseId) {
        log.info("Cleanup all participations for exercise {} in parallel", exerciseId);
        Exercise exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
        if (!(exercise instanceof ProgrammingExercise)) {
            log.warn("Exercise with exerciseId {} is not an instance of ProgrammingExercise. Ignoring the request to cleanup repositories and build plan", exerciseId);
            return;
        }

        // Cleanup in parallel to speedup the process
        try (var threadPool = Executors.newFixedThreadPool(10)) {
            var futures = exercise.getStudentParticipations().stream().map(participation -> CompletableFuture.runAsync(() -> {
                try {
                    participationDeletionService.cleanupBuildPlan((ProgrammingExerciseStudentParticipation) participation);
                    participationDeletionService.cleanupRepository((ProgrammingExerciseStudentParticipation) participation);
                }
                catch (Exception exception) {
                    log.error("Failed to clean the student participation {} for programming exercise {}", participation.getId(), exerciseId);
                }
            }, threadPool).toCompletableFuture()).toArray(CompletableFuture[]::new);
            // wait until all operations finish before returning
            CompletableFuture.allOf(futures).thenRun(threadPool::shutdown).join();
        }
    }

    /**
     * Delete the exercise by id and all its participations.
     *
     * @param exerciseId                the exercise to be deleted
     * @param deleteBaseReposBuildPlans whether the template and solution repos and build plans should be deleted (can be true for programming exercises and should be false for
     *                                      all other exercise types)
     */
    public void delete(long exerciseId, boolean deleteBaseReposBuildPlans) {
        delete(exerciseId, deleteBaseReposBuildPlans, DeletionProgressListener.none());
    }

    /**
     * Delete the exercise by id and all its participations and report the progress.
     *
     * @param exerciseId                the exercise to be deleted
     * @param deleteBaseReposBuildPlans whether the template and solution repos and build plans should be deleted (can be true for programming exercises and should be false for
     *                                      all other exercise types)
     * @param progressListener          told which step runs and how many participations are deleted
     */
    public void delete(long exerciseId, boolean deleteBaseReposBuildPlans, DeletionProgressListener progressListener) {
        var exercise = exerciseRepository.findWithCompetenciesByIdElseThrow(exerciseId);
        if (!(exercise instanceof ProgrammingExercise)) {
            deleteExercise(exercise, deleteBaseReposBuildPlans, progressListener);
            return;
        }
        // Include student participation cleanup as well as the final template/solution cleanup and exercise removal.
        // A processor admitted before the marker must finish its writes before any of these deletion steps can run.
        try (var permit = programmingExerciseDeletionService.beginDeletionAndAwaitResults(exerciseId)) {
            deleteExercise(exercise, deleteBaseReposBuildPlans, progressListener);
        }
    }

    /**
     * Deletes the exercise in the background and reports the progress on the websocket topic of the exercise. Returns as soon as the deletion is scheduled. The caller checks the
     * permissions first.
     *
     * @param exercise                  the exercise to delete
     * @param deleteBaseReposBuildPlans whether the template and solution repos and build plans should be deleted (only relevant for programming exercises)
     * @param onAccepted                runs in the request once the deletion is accepted and before it starts, for example to audit the request, to notify AtlasML or to cancel
     *                                      the schedule of a quiz. It does not run if the exercise, or the exam or exercise group it belongs to, is already being deleted.
     * @throws ConflictException if the exercise, or the exam or exercise group it belongs to, is already being deleted
     */
    public void startAsyncDeletion(Exercise exercise, boolean deleteBaseReposBuildPlans, Runnable onAccepted) {
        startAsyncDeletion(exercise, deleteBaseReposBuildPlans, onAccepted, () -> {
        });
    }

    /**
     * Deletes the exercise in the background, like {@link #startAsyncDeletion(Exercise, boolean, Runnable)}, and undoes what the acceptance did if the exercise is not deleted.
     *
     * @param exercise                  the exercise to delete
     * @param deleteBaseReposBuildPlans whether the template and solution repos and build plans should be deleted (only relevant for programming exercises)
     * @param onAccepted                runs in the request once the deletion is accepted and before it starts, for example to cancel the schedule of a quiz
     * @param onNotDeleted              runs if the deletion fails or cannot be scheduled after the acceptance started, for example to restore the schedule of a quiz. It does not
     *                                      run if the request is refused because the exercise is already being deleted. It has to cope with an exercise that is gone.
     * @throws ConflictException if the exercise, or the exam or exercise group it belongs to, is already being deleted
     */
    public void startAsyncDeletion(Exercise exercise, boolean deleteBaseReposBuildPlans, Runnable onAccepted, Runnable onNotDeleted) {
        long exerciseId = exercise.getId();
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getId();
        boolean examExercise = exerciseRepository.isExamExercise(exerciseId);
        Map<String, Double> stepWeights = EntityDeletionWeights.exerciseStepWeights(exercise, examExercise, participationRepository.countByExerciseId(exerciseId));
        // An exam or an exercise group that is deleted holds the scope of this exercise, so this request is refused with a conflict meanwhile.
        // Set before the acceptance runs, so a failure in the middle of it is undone as well.
        AtomicBoolean acceptanceStarted = new AtomicBoolean();
        boolean started = false;
        try {
            var target = new DeletionTarget(EntityDeletionType.EXERCISE, exerciseId, ExerciseWebsocketTopics.DELETION_PROGRESS.at(exerciseId));
            entityDeletionProgressService.startAsync(EntityDeletionProgressService.exerciseScopeKey(exerciseId), List.of(), target, stepWeights.size(),
                    EntityDeletionWeights.total(stepWeights), () -> {
                        acceptanceStarted.set(true);
                        entityDeletionProgressService.rememberCourse(EntityDeletionProgressService.exerciseScopeKey(exerciseId), courseId);
                        onAccepted.run();
                    }, tracker -> {
                        boolean deleted = false;
                        try {
                            var reporter = new DeletionStepReporter(tracker, stepWeights);
                            delete(exerciseId, deleteBaseReposBuildPlans, reporter);
                            reporter.finishCurrentStep();
                            deleted = true;
                        }
                        finally {
                            // Whatever ended the job, also an Error, undoes what the acceptance did.
                            if (!deleted) {
                                undoAcceptance(exerciseId, onNotDeleted);
                            }
                        }
                    });
            started = true;
        }
        finally {
            if (!started && acceptanceStarted.get()) {
                undoAcceptance(exerciseId, onNotDeleted);
            }
        }
    }

    private void undoAcceptance(long exerciseId, Runnable onNotDeleted) {
        try {
            onNotDeleted.run();
        }
        catch (RuntimeException e) {
            log.warn("Could not undo the acceptance of the deletion of exercise {}", exerciseId, e);
        }
    }

    private void deleteExercise(Exercise exercise, boolean deleteBaseReposBuildPlans, DeletionProgressListener progressListener) {
        long exerciseId = exercise.getId();
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();
        log.info("Request to delete {} with id {}", exercise.getClass().getSimpleName(), exerciseId);

        long start = System.nanoTime();
        progressListener.stepStarted(EntityDeletionWeights.STEP_CHANNEL);
        channelService.deleteChannelForExerciseId(exerciseId);
        log.debug("Deleting the channel took {}", TimeLogUtil.formatDurationFrom(start));

        if (exercise instanceof TextExercise) {
            log.info("Cancel scheduled operations of exercise {}", exercise.getId());
            textApi.ifPresent(api -> api.cancelScheduledOperations(exerciseId));
        }

        progressListener.stepStarted(EntityDeletionWeights.STEP_PLAGIARISM);
        // delete all exercise units linking to the exercise
        lectureUnitApi.ifPresent(api -> api.removeLectureUnitFromExercise(exerciseId));

        // delete all plagiarism results belonging to this exercise
        plagiarismResultApi.ifPresent(api -> api.deletePlagiarismResultsByExerciseId(exerciseId));

        // delete all participations belonging to this exercise, this will also delete submissions, results, feedback, complaints, etc.
        progressListener.stepStarted(EntityDeletionWeights.STEP_PARTICIPATIONS);
        participationDeletionService.deleteAllByExercise(exercise, false, progressListener);

        // clean up the many-to-many relationship to avoid problems when deleting the entities but not the relationship table
        progressListener.stepStarted(EntityDeletionWeights.STEP_EXAMPLE_SUBMISSIONS);
        exercise = exerciseRepository.findByIdWithEagerExampleSubmissionsElseThrow(exerciseId);
        exercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmissionService.deleteById(exampleSubmission.getId()));
        exercise.setExampleSubmissions(new HashSet<>());

        // make sure tutor participations are deleted before the exercise is deleted
        tutorParticipationRepository.deleteAllByAssessedExerciseId(exercise.getId());

        if (exercise.isExamExercise()) {
            progressListener.stepStarted(EntityDeletionWeights.STEP_STUDENT_EXAMS);
            StudentExamApi api = studentExamApi.orElseThrow(() -> new ExamApiNotPresentException(StudentExamApi.class));
            Set<StudentExam> studentExams = api.findAllWithExercisesByExamId(exercise.getExerciseGroup().getExam().getId());
            for (StudentExam studentExam : studentExams) {
                if (studentExam.getExercises().contains(exercise)) {
                    // remove exercise reference from student exam
                    studentExam.removeExercise(exercise);
                    api.save(studentExam);
                }
            }
        }

        // Remove any Iris chat sessions referencing this exercise. Since the unified iris_session
        // schema uses a plain entity_id column (no FK), cleanup must happen explicitly here.
        if (exercise instanceof ProgrammingExercise) {
            progressListener.stepStarted(EntityDeletionWeights.STEP_IRIS_SESSIONS);
            irisChatSessionApi.ifPresent(api -> api.deleteAllForProgrammingExercise(exerciseId));
        }
        else if (exercise instanceof TextExercise) {
            progressListener.stepStarted(EntityDeletionWeights.STEP_IRIS_SESSIONS);
            irisChatSessionApi.ifPresent(api -> api.deleteAllForTextExercise(exerciseId));
        }

        // Programming exercises have some special stuff that needs to be cleaned up (solution/template participation, build plans, etc.).
        if (exercise instanceof ProgrammingExercise) {
            progressListener.stepStarted(EntityDeletionWeights.STEP_PROGRAMMING_ARTIFACTS);
            programmingExerciseDeletionService.delete(exercise.getId(), deleteBaseReposBuildPlans);
        }
        else {
            progressListener.stepStarted(EntityDeletionWeights.STEP_EXERCISE);
            // Quiz drag-and-drop image files were formerly removed by @PostRemove callbacks on the (now JSON-backed) drag items and questions. Since these are no longer JPA
            // entities, every quiz deletion entry point must trigger the file cleanup explicitly; doing it here in the shared deletion path covers course, exam, and
            // exercise-group deletions, not only the direct REST deletion in QuizExerciseDeletionResource.
            // The paths have to be collected BEFORE the exercise row disappears (they live in the question JSON), but the files must only be deleted AFTER the deletion
            // succeeded — deleting them first would leave a still-existing quiz without its authored images if the refetch or delete below fails.
            List<Path> dragAndDropImagesToDelete = exercise instanceof QuizExercise ? quizExerciseService.collectDragAndDropImagePaths(exerciseId) : List.of();
            // fetch the exercise again to allow Hibernate to delete it properly
            exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
            exerciseRepository.delete(exercise);
            FileUtil.deleteFiles(dragAndDropImagesToDelete);
        }

        // The exercise is deleted at this point, so the cleanup below must run even if the progress cannot be reported.
        progressListener.stepStartedAfterDeletion(EntityDeletionWeights.STEP_CLEANUP);
        competencyProgressApi.ifPresent(api -> competencyLinks.stream().map(CompetencyExerciseLink::getCompetency).forEach(api::updateProgressByCompetencyAsync));

        searchableItemWeaviateService.ifPresent(service -> service.deleteEntityAsync(SearchableEntitySchema.TypeValues.EXERCISE, exerciseId));
    }

    /**
     * Resets an Exercise by deleting all its participations and plagiarism results.
     * This overload fetches the exercise by ID, avoiding the need to load the full entity beforehand.
     * Prefer this method when only the exercise ID is available to avoid unnecessary database queries.
     *
     * @param exerciseId the ID of the exercise to reset
     */
    public void reset(long exerciseId) {
        log.debug("Request reset Exercise : {}", exerciseId);

        // Delete plagiarism results by ID (no need to fetch exercise)
        plagiarismResultApi.ifPresent(api -> api.deletePlagiarismResultsByExerciseId(exerciseId));

        // Fetch minimal exercise data needed for participation deletion
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        // Delete all participations - this handles submissions, results, feedback, complaints, etc.
        // For exam exercises, we don't need to recalculate competency progress since exam reset handles cleanup
        boolean isExamExercise = exercise.isExamExercise();
        participationDeletionService.deleteAllByExercise(exercise, !isExamExercise);

        // Quiz-specific reset is only needed for course exercises
        if (exercise instanceof QuizExercise && exercise.isCourseExercise()) {
            quizExerciseService.resetExercise(exerciseId);
        }
    }

    /**
     * Deletes all plagiarism results and participations for an exercise.
     *
     * @param exercise for which the plagiarism results and participations should be deleted
     */
    public void deletePlagiarismResultsAndParticipations(Exercise exercise) {
        // delete all plagiarism results for this exercise
        plagiarismResultApi.ifPresent(api -> api.deletePlagiarismResultsByExerciseId(exercise.getId()));

        // delete all participations belonging to this exercise, this will also delete submissions, results, feedback, complaints, etc.
        // TODO: recalculateCompetencyProgress = true does not make sense here for exam exercises
        participationDeletionService.deleteAllByExercise(exercise, true);
    }
}
