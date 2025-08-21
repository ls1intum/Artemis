package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.ExampleSubmissionService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
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

    private final Optional<IrisSettingsApi> irisSettingsApi;

    public ExerciseDeletionService(ExerciseRepository exerciseRepository, ParticipationDeletionService participationDeletionService,
            ProgrammingExerciseDeletionService programmingExerciseDeletionService, QuizExerciseService quizExerciseService,
            TutorParticipationRepository tutorParticipationRepository, ExampleSubmissionService exampleSubmissionService, Optional<StudentExamApi> studentExamApi,
            Optional<LectureUnitApi> lectureUnitApi, Optional<PlagiarismResultApi> plagiarismResultApi, Optional<TextApi> textApi, ChannelService channelService,
            Optional<CompetencyProgressApi> competencyProgressApi, Optional<IrisSettingsApi> irisSettingsApi) {
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
        this.irisSettingsApi = irisSettingsApi;
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
                    // participationDeletionService.cleanupBuildPlan((ProgrammingExerciseStudentParticipation) participation);
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
        var exercise = exerciseRepository.findWithCompetenciesByIdElseThrow(exerciseId);
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();
        log.info("Request to delete {} with id {}", exercise.getClass().getSimpleName(), exerciseId);

        long start = System.nanoTime();
        channelService.deleteChannelForExerciseId(exerciseId);
        log.debug("Deleting the channel took {}", TimeLogUtil.formatDurationFrom(start));

        if (exercise instanceof TextExercise) {
            log.info("Cancel scheduled operations of exercise {}", exercise.getId());
            textApi.ifPresent(api -> api.cancelScheduledOperations(exerciseId));
        }

        // delete all exercise units linking to the exercise
        lectureUnitApi.ifPresent(api -> api.removeLectureUnitFromExercise(exerciseId));

        // delete all iris settings for this exercise
        irisSettingsApi.ifPresent(api -> api.deleteSettingsForExercise(exerciseId));

        // delete all plagiarism results belonging to this exercise
        plagiarismResultApi.ifPresent(api -> api.deletePlagiarismResultsByExerciseId(exerciseId));

        // delete all participations belonging to this exercise, this will also delete submissions, results, feedback, complaints, etc.
        participationDeletionService.deleteAllByExercise(exercise, false);

        // clean up the many-to-many relationship to avoid problems when deleting the entities but not the relationship table
        exercise = exerciseRepository.findByIdWithEagerExampleSubmissionsElseThrow(exerciseId);
        exercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmissionService.deleteById(exampleSubmission.getId()));
        exercise.setExampleSubmissions(new HashSet<>());

        // make sure tutor participations are deleted before the exercise is deleted
        tutorParticipationRepository.deleteAllByAssessedExerciseId(exercise.getId());

        if (exercise.isExamExercise()) {
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

        // Programming exercises have some special stuff that needs to be cleaned up (solution/template participation, build plans, etc.).
        if (exercise instanceof ProgrammingExercise) {
            programmingExerciseDeletionService.delete(exercise.getId(), deleteBaseReposBuildPlans);
        }
        else {
            // fetch the exercise again to allow Hibernate to delete it properly
            exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
            exerciseRepository.delete(exercise);
        }

        competencyProgressApi.ifPresent(api -> competencyLinks.stream().map(CompetencyExerciseLink::getCompetency).forEach(api::updateProgressByCompetencyAsync));
    }

    /**
     * Resets an Exercise by deleting all its participations and plagiarism results
     *
     * @param exercise which should be reset
     */
    public void reset(Exercise exercise) {
        log.debug("Request reset Exercise : {}", exercise.getId());

        deletePlagiarismResultsAndParticipations(exercise);

        // and additional call to the quizExerciseService is only needed for course exercises, not for exam exercises
        if (exercise instanceof QuizExercise && exercise.isCourseExercise()) {
            quizExerciseService.resetExercise(exercise.getId());
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
