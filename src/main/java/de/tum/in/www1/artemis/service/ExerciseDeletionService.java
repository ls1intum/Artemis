package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Exercise.
 */
@Service
public class ExerciseDeletionService {

    private final Logger log = LoggerFactory.getLogger(ExerciseDeletionService.class);

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

    private final TextAssessmentKnowledgeService textAssessmentKnowledgeService;

    private final ModelAssessmentKnowledgeService modelAssessmentKnowledgeService;

    private final TextExerciseRepository textExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    public ExerciseDeletionService(ExerciseRepository exerciseRepository, ExerciseUnitRepository exerciseUnitRepository, ParticipationService participationService,
            ProgrammingExerciseService programmingExerciseService, ModelingExerciseService modelingExerciseService, QuizExerciseService quizExerciseService,
            TutorParticipationRepository tutorParticipationRepository, ExampleSubmissionService exampleSubmissionService, StudentExamRepository studentExamRepository,
            LectureUnitService lectureUnitService, TextExerciseRepository textExerciseRepository, PlagiarismResultRepository plagiarismResultRepository,
            TextAssessmentKnowledgeService textAssessmentKnowledgeService, ModelingExerciseRepository modelingExerciseRepository,
            ModelAssessmentKnowledgeService modelAssessmentKnowledgeService) {
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
        this.textAssessmentKnowledgeService = textAssessmentKnowledgeService;
        this.modelAssessmentKnowledgeService = modelAssessmentKnowledgeService;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
    }

    /**
     * Delete student build plans (except BASE/SOLUTION) and optionally git repositories of all exercise student participations.
     *
     * @param exerciseId         programming exercise for which build plans in respective student participations are deleted
     * @param deleteRepositories if true, the repositories gets deleted
     */
    public void cleanup(Long exerciseId, boolean deleteRepositories) {
        Exercise exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
        log.info("Request to cleanup all participations for Exercise : {}", exercise.getTitle());

        if (exercise instanceof ProgrammingExercise) {
            for (StudentParticipation participation : exercise.getStudentParticipations()) {
                participationService.cleanupBuildPlan((ProgrammingExerciseStudentParticipation) participation);
            }

            if (!deleteRepositories) {
                return; // in this case, we are done
            }

            for (StudentParticipation participation : exercise.getStudentParticipations()) {
                participationService.cleanupRepository((ProgrammingExerciseStudentParticipation) participation);
            }

        }
        else {
            log.warn("Exercise with exerciseId {} is not an instance of ProgrammingExercise. Ignoring the request to cleanup repositories and build plan", exerciseId);
        }
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
        var exercise = exerciseRepository.findByIdWithLearningGoalsElseThrow(exerciseId);
        log.info("Request to delete {} with id {}", exercise.getClass().getSimpleName(), exerciseId);

        if (exercise instanceof ModelingExercise modelingExercise) {
            log.info("Deleting clusters, elements and cancel scheduled operations of exercise {}", exercise.getId());

            modelingExerciseService.deleteClustersAndElements(modelingExercise);
            modelingExerciseService.cancelScheduledOperations(exerciseId);
        }

        // delete all exercise units linking to the exercise
        List<ExerciseUnit> exerciseUnits = this.exerciseUnitRepository.findByIdWithLearningGoalsBidirectional(exerciseId);
        for (ExerciseUnit exerciseUnit : exerciseUnits) {
            lectureUnitService.removeLectureUnit(exerciseUnit);
        }

        // delete all plagiarism results belonging to this exercise
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exerciseId);

        // delete all participations belonging to this exercise, this will also delete submissions, results, feedback, complaints, etc.
        participationService.deleteAllByExerciseId(exercise.getId(), deleteStudentReposBuildPlans, deleteStudentReposBuildPlans);

        // clean up the many-to-many relationship to avoid problems when deleting the entities but not the relationship table
        exercise = exerciseRepository.findByIdWithEagerExampleSubmissions(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
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
            // delete text assessment knowledge if exercise is of type TextExercise and if no other exercise uses same knowledge
            if (exercise instanceof TextExercise textExercise) {
                // explicitly load the text exercise as such so that the knowledge is eagerly loaded as well
                textExercise = textExerciseRepository.findByIdElseThrow(exercise.getId());
                if (textExercise.getKnowledge() != null) {
                    long knowledgeId = textExercise.getKnowledge().getId();
                    // Remove knowledge to avoid foreign key constraint exception
                    textExercise.setKnowledge(null);
                    textExerciseRepository.save(textExercise);
                    textAssessmentKnowledgeService.deleteKnowledgeIfUnused(knowledgeId);
                }
            }
            // delete model assessment knowledge if exercise is of type ModelExercise and if no other exercise uses same knowledge
            else if (exercise instanceof ModelingExercise modelingExercise) {
                // explicitly load the modeling exercise as such so that the knowledge is eagerly loaded as well
                modelingExercise = modelingExerciseRepository.findByIdElseThrow(exercise.getId());
                if (modelingExercise.getKnowledge() != null) {
                    long knowledgeId = modelingExercise.getKnowledge().getId();
                    // Remove knowledge to avoid foreign key constraint exception
                    modelingExercise.setKnowledge(null);
                    modelingExerciseRepository.save(modelingExercise);
                    modelAssessmentKnowledgeService.deleteKnowledgeIfUnused(knowledgeId);
                }
            }

            // fetch the exercise again to allow Hibernate to delete it properly
            exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
            exerciseRepository.delete(exercise);
        }
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
        participationService.deleteAllByExerciseId(exercise.getId(), true, true);
    }
}
