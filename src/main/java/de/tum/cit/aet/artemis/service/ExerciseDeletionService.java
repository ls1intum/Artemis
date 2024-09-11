package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.domain.exam.StudentExam;
import de.tum.cit.aet.artemis.domain.lecture.ExerciseUnit;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.domain.modeling.ModelingExercise;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.domain.quiz.QuizExercise;
import de.tum.cit.aet.artemis.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseService;
import de.tum.cit.aet.artemis.service.quiz.QuizExerciseService;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;

/**
 * Service Implementation for managing Exercise.
 */
@Profile(PROFILE_CORE)
@Service
public class ExerciseDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseDeletionService.class);

    private final ParticipationService participationService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ModelingExerciseService modelingExerciseService;

    private final QuizExerciseService quizExerciseService;

    private final ExampleSubmissionService exampleSubmissionService;

    private final StudentExamRepository studentExamRepository;

    private final ExerciseUnitRepository exerciseUnitRepository;

    private final ExerciseRepository exerciseRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final LectureUnitService lectureUnitService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final TextExerciseService textExerciseService;

    private final ChannelRepository channelRepository;

    private final ChannelService channelService;

    private final CompetencyProgressService competencyProgressService;

    public ExerciseDeletionService(ExerciseRepository exerciseRepository, ExerciseUnitRepository exerciseUnitRepository, ParticipationService participationService,
            ProgrammingExerciseService programmingExerciseService, ModelingExerciseService modelingExerciseService, QuizExerciseService quizExerciseService,
            TutorParticipationRepository tutorParticipationRepository, ExampleSubmissionService exampleSubmissionService, StudentExamRepository studentExamRepository,
            LectureUnitService lectureUnitService, PlagiarismResultRepository plagiarismResultRepository, TextExerciseService textExerciseService,
            ChannelRepository channelRepository, ChannelService channelService, CompetencyProgressService competencyProgressService) {
        this.exerciseRepository = exerciseRepository;
        this.participationService = participationService;
        this.programmingExerciseService = programmingExerciseService;
        this.modelingExerciseService = modelingExerciseService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleSubmissionService = exampleSubmissionService;
        this.quizExerciseService = quizExerciseService;
        this.studentExamRepository = studentExamRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.lectureUnitService = lectureUnitService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.textExerciseService = textExerciseService;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * Delete student build plans (except BASE/SOLUTION) and optionally git repositories of all exercise student participations.
     *
     * @param exerciseId         programming exercise for which build plans in respective student participations are deleted
     * @param deleteRepositories if true, the repositories gets deleted
     */
    public void cleanup(Long exerciseId, boolean deleteRepositories) {
        log.info("Cleanup all participations for exercise {} in parallel", exerciseId);
        Exercise exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
        if (!(exercise instanceof ProgrammingExercise)) {
            log.warn("Exercise with exerciseId {} is not an instance of ProgrammingExercise. Ignoring the request to cleanup repositories and build plan", exerciseId);
            return;
        }

        // Cleanup in parallel to speedup the process
        var threadPool = Executors.newFixedThreadPool(10);
        var futures = exercise.getStudentParticipations().stream().map(participation -> CompletableFuture.runAsync(() -> {
            try {
                participationService.cleanupBuildPlan((ProgrammingExerciseStudentParticipation) participation);
                if (!deleteRepositories) {
                    return; // in this case, we are done with the participation
                }
                participationService.cleanupRepository((ProgrammingExerciseStudentParticipation) participation);
            }
            catch (Exception exception) {
                log.error("Failed to clean the student participation {} for programming exercise {}", participation.getId(), exerciseId);
            }
        }, threadPool).toCompletableFuture()).toArray(CompletableFuture[]::new);
        // wait until all operations finish before returning
        CompletableFuture.allOf(futures).thenRun(threadPool::shutdown).join();
    }

    /**
     * Delete the exercise by id and all its participations.
     *
     * @param exerciseId                   the exercise to be deleted
     * @param deleteStudentReposBuildPlans whether the student repos and build plans should be deleted (can be true for programming exercises and should be false for all other
     *                                         exercise types)
     * @param deleteBaseReposBuildPlans    whether the template and solution repos and build plans should be deleted (can be true for programming exercises and should be false for
     *                                         all other exercise types)
     */
    public void delete(long exerciseId, boolean deleteStudentReposBuildPlans, boolean deleteBaseReposBuildPlans) {
        var exercise = exerciseRepository.findWithCompetenciesByIdElseThrow(exerciseId);
        Set<CourseCompetency> competencies = exercise.getCompetencies();
        log.info("Request to delete {} with id {}", exercise.getClass().getSimpleName(), exerciseId);

        long start = System.nanoTime();
        Channel exerciseChannel = channelRepository.findChannelByExerciseId(exerciseId);
        channelService.deleteChannel(exerciseChannel);
        log.info("Deleting the channel took {}", TimeLogUtil.formatDurationFrom(start));

        if (exercise instanceof ModelingExercise modelingExercise) {
            log.info("Deleting clusters, elements and cancel scheduled operations of exercise {}", exercise.getId());

            modelingExerciseService.deleteClustersAndElements(modelingExercise);
            modelingExerciseService.cancelScheduledOperations(exerciseId);
        }

        if (exercise instanceof TextExercise) {
            log.info("Cancel scheduled operations of exercise {}", exercise.getId());
            textExerciseService.cancelScheduledOperations(exerciseId);
        }

        // delete all exercise units linking to the exercise
        List<ExerciseUnit> exerciseUnits = this.exerciseUnitRepository.findByIdWithCompetenciesBidirectional(exerciseId);
        for (ExerciseUnit exerciseUnit : exerciseUnits) {
            lectureUnitService.removeLectureUnit(exerciseUnit);
        }

        // delete all plagiarism results belonging to this exercise
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exerciseId);

        // delete all participations belonging to this exercise, this will also delete submissions, results, feedback, complaints, etc.
        participationService.deleteAllByExercise(exercise, deleteStudentReposBuildPlans, deleteStudentReposBuildPlans, false);

        // clean up the many-to-many relationship to avoid problems when deleting the entities but not the relationship table
        exercise = exerciseRepository.findByIdWithEagerExampleSubmissionsElseThrow(exerciseId);
        exercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmissionService.deleteById(exampleSubmission.getId()));
        exercise.setExampleSubmissions(new HashSet<>());

        // make sure tutor participations are deleted before the exercise is deleted
        tutorParticipationRepository.deleteAllByAssessedExerciseId(exercise.getId());

        if (exercise.isExamExercise()) {
            Set<StudentExam> studentExams = studentExamRepository.findAllWithExercisesByExamId(exercise.getExerciseGroup().getExam().getId());
            for (StudentExam studentExam : studentExams) {
                if (studentExam.getExercises().contains(exercise)) {
                    // remove exercise reference from student exam
                    studentExam.removeExercise(exercise);
                    studentExamRepository.save(studentExam);
                }
            }
        }

        // Programming exercises have some special stuff that needs to be cleaned up (solution/template participation, build plans, etc.).
        if (exercise instanceof ProgrammingExercise) {
            programmingExerciseService.delete(exercise.getId(), deleteBaseReposBuildPlans);
        }
        else {
            // fetch the exercise again to allow Hibernate to delete it properly
            exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
            exerciseRepository.delete(exercise);
        }

        competencies.forEach(competencyProgressService::updateProgressByCompetencyAsync);
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
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exercise.getId());

        // delete all participations belonging to this exercise, this will also delete submissions, results, feedback, complaints, etc.
        participationService.deleteAllByExercise(exercise, true, true, true);
    }
}
