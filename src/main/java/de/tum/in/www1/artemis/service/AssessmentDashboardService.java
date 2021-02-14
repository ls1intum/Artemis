package de.tum.in.www1.artemis.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;

/**
 * Service Implementation for managing Tutor-Assessment-Dashboard.
 */
@Service
public class AssessmentDashboardService {

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final ComplaintService complaintService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final SubmissionService submissionService;

    private final ResultService resultService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    public AssessmentDashboardService(ComplaintService complaintService, ProgrammingExerciseService programmingExerciseService, SubmissionService submissionService,
            ResultService resultService, ExampleSubmissionRepository exampleSubmissionRepository) {
        this.complaintService = complaintService;
        this.programmingExerciseService = programmingExerciseService;
        this.submissionService = submissionService;
        this.resultService = resultService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
    }

    /**
     * Prepares the exercises for the assessment dashboard by setting the tutor participations and statistics
     *
     * @param exercises exercises to be prepared for the assessment dashboard
     * @param tutorParticipations participations of the tutors
     * @param examMode flag should be set for exam dashboard
     */
    public void prepareExercisesForAssessmentDashboard(Set<Exercise> exercises, List<TutorParticipation> tutorParticipations, boolean examMode) {
        for (Exercise exercise : exercises) {

            DueDateStat numberOfSubmissions;
            DueDateStat totalNumberOfAssessments;

            if (exercise instanceof ProgrammingExercise) {
                numberOfSubmissions = new DueDateStat(programmingExerciseService.countSubmissionsByExerciseIdSubmitted(exercise.getId(), examMode), 0L);
                totalNumberOfAssessments = new DueDateStat(programmingExerciseService.countAssessmentsByExerciseIdSubmitted(exercise.getId(), examMode), 0L);
            }
            else {
                numberOfSubmissions = submissionService.countSubmissionsForExercise(exercise.getId(), examMode);
                totalNumberOfAssessments = resultService.countNumberOfFinishedAssessmentsForExercise(exercise.getId(), examMode);
            }

            exercise.setNumberOfSubmissions(numberOfSubmissions);
            exercise.setTotalNumberOfAssessments(totalNumberOfAssessments);

            final DueDateStat[] numberOfAssessmentsOfCorrectionRounds = resultService.calculateNrOfAssessmentsOfCorrectionRoundsForDashboard(exercise, examMode,
                    totalNumberOfAssessments);
            exercise.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);

            complaintService.calculateNrOfOpenComplaints(exercise, examMode);

            Set<ExampleSubmission> exampleSubmissions = exampleSubmissionRepository.findAllWithEagerResultByExerciseId(exercise.getId());

            // Do not provide example submissions without any assessment
            exampleSubmissions.removeIf(exampleSubmission -> exampleSubmission.getSubmission() == null || exampleSubmission.getSubmission().getLatestResult() == null);
            exercise.setExampleSubmissions(exampleSubmissions);

            TutorParticipation tutorParticipation = tutorParticipations.stream().filter(participation -> participation.getAssessedExercise().getId().equals(exercise.getId()))
                    .findFirst().orElseGet(() -> {
                        TutorParticipation emptyTutorParticipation = new TutorParticipation();
                        emptyTutorParticipation.setStatus(TutorParticipationStatus.NOT_PARTICIPATED);
                        return emptyTutorParticipation;
                    });
            exercise.setTutorParticipations(Collections.singleton(tutorParticipation));
        }
    }
}
