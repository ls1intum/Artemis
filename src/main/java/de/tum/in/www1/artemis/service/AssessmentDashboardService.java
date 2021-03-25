package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.assessmentDashboard.AssessmentDashboardExerciseMapEntry;
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
     * This is very slow as each iteration takes about 2.5 s
     * @param exercises exercises to be prepared for the assessment dashboard
     * @param tutorParticipations participations of the tutors
     * @param examMode flag should be set for exam dashboard
     */
    public void generateStatisticsForExercisesForAssessmentDashboard(Set<Exercise> exercises, List<TutorParticipation> tutorParticipations, boolean examMode) {
        log.info("generateStatisticsForExercisesForAssessmentDashboard invoked");
        // start measures performance of each individual query, start2 measures performance of one loop iteration
        long start = System.nanoTime();
        long start2 = System.nanoTime();
        long startComplete = System.nanoTime();
        Set<Exercise> programmingExerciseIds = exercises.stream().filter(exercise -> exercise instanceof ProgrammingExercise).collect(Collectors.toSet());
        Set<Exercise> nonProgrammingExerciseIds = exercises.stream().filter(exercise -> !(exercise instanceof ProgrammingExercise)).collect(Collectors.toSet());

        complaintService.calculateNrOfOpenComplaints(exercises, examMode);
        log.info("Finished >> complaintService.calculateNrOfOpenComplaints all << in " + TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        calculateNumberOfSubmissions(programmingExerciseIds, nonProgrammingExerciseIds, examMode);
        log.info("Finished >> assessmentDashboardService.calculateNumberOfSubmissions all << in " + TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        // This method dos not yet fetch the assessment data
        calculateNumberOfAssessments(programmingExerciseIds, nonProgrammingExerciseIds, examMode);
        log.info("Finished >> assessmentDashboardService.calculateNumberOfAssessments all << in " + TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        // parts of this loop can possibly still be extracted.
        for (Exercise exercise : exercises) {
            DueDateStat totalNumberOfAssessments;

            if (exercise instanceof ProgrammingExercise) {
                totalNumberOfAssessments = new DueDateStat(programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted(exercise.getId(), examMode), 0L);
                log.info("Finished >> programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted << call for exercise " + exercise.getId() + " in "
                        + TimeLogUtil.formatDurationFrom(start));
                start = System.nanoTime();
            }
            else {
                totalNumberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exercise.getId(), examMode);
                log.info("Finished >> resultRepository.countNumberOfFinishedAssessmentsForExercise << call for exercise " + exercise.getId() + " in "
                        + TimeLogUtil.formatDurationFrom(start));
                start = System.nanoTime();
            }

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
        }
        log.info("Finished >> generateStatisticsForExercisesForAssessmentDashboard << call in " + TimeLogUtil.formatDurationFrom(startComplete));
    }

    private void calculateNumberOfAssessments(Set<Exercise> programmingExercises, Set<Exercise> nonProgrammingExercises, boolean examMode) {
        return;
    }

    // TODO add comment
    private void calculateNumberOfSubmissions(Set<Exercise> programmingExercises, Set<Exercise> nonProgrammingExercises, boolean examMode) {
        final List<AssessmentDashboardExerciseMapEntry> numberOfSubmissionsOfProgrammingExercises;
        final List<AssessmentDashboardExerciseMapEntry> numberOfSubmissionsOfNonProgrammingExercises;
        final List<AssessmentDashboardExerciseMapEntry> numberOfLateSubmissionsOfNonProgrammingExercises;
        Set<Long> programmingExerciseIds = programmingExercises.stream().map(exercise -> exercise.getId()).collect(Collectors.toSet());
        Set<Long> nonProgrammingExerciseIds = nonProgrammingExercises.stream().map(exercise -> exercise.getId()).collect(Collectors.toSet());

        if (examMode) {
            numberOfSubmissionsOfProgrammingExercises = programmingExerciseRepository.countSubmissionsByExerciseIdsSubmittedIgnoreTestRun(programmingExerciseIds);
            numberOfSubmissionsOfNonProgrammingExercises = submissionRepository.countByExerciseIdsSubmittedBeforeDueDateIgnoreTestRuns(nonProgrammingExerciseIds);
            numberOfLateSubmissionsOfNonProgrammingExercises = new ArrayList<>();
        }
        else {
            numberOfSubmissionsOfProgrammingExercises = programmingExerciseRepository.countSubmissionsByExerciseIdsSubmitted(programmingExerciseIds);
            numberOfSubmissionsOfNonProgrammingExercises = submissionRepository.countByExerciseIdsSubmittedBeforeDueDate(nonProgrammingExerciseIds);
            numberOfLateSubmissionsOfNonProgrammingExercises = submissionRepository.countByExerciseIdsSubmittedAfterDueDate(nonProgrammingExerciseIds);
        }
        var numberOfSubmissionsOfProgrammingExercisesMap = numberOfSubmissionsOfProgrammingExercises.stream()
                .collect(Collectors.toMap(AssessmentDashboardExerciseMapEntry::getKey, entry -> entry.getValue()));
        var numberOfSubmissionsOfNonProgrammingExercisesMap = numberOfSubmissionsOfNonProgrammingExercises.stream()
                .collect(Collectors.toMap(AssessmentDashboardExerciseMapEntry::getKey, entry -> entry.getValue()));
        var numberOfLateSubmissionsOfNonProgrammingExercisesMap = numberOfLateSubmissionsOfNonProgrammingExercises.stream()
                .collect(Collectors.toMap(AssessmentDashboardExerciseMapEntry::getKey, entry -> entry.getValue()));

        programmingExercises.forEach(exercise -> {
            exercise.setNumberOfSubmissions(new DueDateStat(numberOfSubmissionsOfProgrammingExercisesMap.getOrDefault(exercise.getId(), 0L), 0L));
        });
        nonProgrammingExercises.forEach(exercise -> {
            exercise.setNumberOfSubmissions(new DueDateStat(numberOfSubmissionsOfNonProgrammingExercisesMap.getOrDefault(exercise.getId(), 0L),
                    numberOfLateSubmissionsOfNonProgrammingExercisesMap.getOrDefault(exercise.getId(), 0L)));
        });
    }
}
