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
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;

/**
 * Service Implementation for managing Tutor-Assessment-Dashboard.
 */
@Service
public class AssessmentDashboardService {

    private final Logger log = LoggerFactory.getLogger(AssessmentDashboardService.class);

    private final ComplaintService complaintService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    public AssessmentDashboardService(ComplaintService complaintService, ProgrammingExerciseRepository programmingExerciseRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository, ExampleSubmissionRepository exampleSubmissionRepository) {
        this.complaintService = complaintService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
    }

    /**
     * Prepares the exercises for the assessment dashboard by setting the tutor participations and statistics
     *
     * @param exercises exercises to be prepared for the assessment dashboard
     * @param tutorParticipations participations of the tutors
     * @param examMode flag should be set for exam dashboard
     */
    public void generateStatisticsForExercisesForAssessmentDashboard(Set<Exercise> exercises, List<TutorParticipation> tutorParticipations, boolean examMode) {
        log.info("generateStatisticsForExercisesForAssessmentDashboard invoked");
        // start measures performance of each individual query, start2 measures performance of one loop iteration
        long start = System.nanoTime();
        long start2 = System.nanoTime();

        for (Exercise exercise : exercises) {

            DueDateStat numberOfSubmissions;
            DueDateStat totalNumberOfAssessments;

            if (exercise instanceof ProgrammingExercise) {
                numberOfSubmissions = new DueDateStat(programmingExerciseRepository.countSubmissionsByExerciseIdSubmitted(exercise.getId(), examMode), 0L);
                log.info("Finished >> programmingExerciseRepository.countSubmissionsByExerciseIdSubmitted << call for exercise " + exercise.getId() + " in "
                        + TimeLogUtil.formatDurationFrom(start));
                start = System.nanoTime();
                totalNumberOfAssessments = new DueDateStat(programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted(exercise.getId(), examMode), 0L);
                log.info("Finished >> programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted << call for exercise " + exercise.getId() + " in "
                        + TimeLogUtil.formatDurationFrom(start));
                start = System.nanoTime();
            }
            else {
                numberOfSubmissions = submissionRepository.countSubmissionsForExercise(exercise.getId(), examMode);
                log.info("Finished >> submissionRepository.countSubmissionsForExercise << call for exercise " + exercise.getId() + " in " + TimeLogUtil.formatDurationFrom(start));
                start = System.nanoTime();
                totalNumberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exercise.getId(), examMode);
                log.info("Finished >> resultRepository.countNumberOfFinishedAssessmentsForExercise << call for exercise " + exercise.getId() + " in "
                        + TimeLogUtil.formatDurationFrom(start));
                start = System.nanoTime();
            }
            exercise.setNumberOfSubmissions(numberOfSubmissions);

            final DueDateStat[] numberOfAssessmentsOfCorrectionRounds;
            if (examMode) {
                // set number of corrections specific to each correction round
                int numberOfCorrectionRounds = exercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam();
                numberOfAssessmentsOfCorrectionRounds = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds);
                log.info("Finished >> resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds << call for exercise " + exercise.getId() + " in "
                        + TimeLogUtil.formatDurationFrom(start));
                start = System.nanoTime();
            }
            else {
                // no examMode here, so correction rounds defaults to 1 and is the same as totalNumberOfAssessments
                numberOfAssessmentsOfCorrectionRounds = new DueDateStat[] { totalNumberOfAssessments };
            }

            exercise.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);
            exercise.setTotalNumberOfAssessments(numberOfAssessmentsOfCorrectionRounds[0]);

            complaintService.calculateNrOfOpenComplaints(exercise, examMode);
            log.info("Finished >> complaintService.calculateNrOfOpenComplaints << call for exercise " + exercise.getId() + " in " + TimeLogUtil.formatDurationFrom(start));
            start = System.nanoTime();
            Set<ExampleSubmission> exampleSubmissions = exampleSubmissionRepository.findAllWithResultByExerciseId(exercise.getId());

            log.info("Finished >> exampleSubmissionRepository.findAllWithResultByExerciseId << call for course " + exercise.getId() + " in "
                    + TimeLogUtil.formatDurationFrom(start));
            start = System.nanoTime();

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

            log.info("Finished >> assessmentDashboardLoopIteration << call for exercise " + exercise.getId() + " in " + TimeLogUtil.formatDurationFrom(start2));
            start2 = System.nanoTime();
        }
    }
}
