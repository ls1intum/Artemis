package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntry;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.dto.DueDateStat;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

/**
 * Service Implementation for managing Tutor-Assessment-Dashboard.
 */
@Profile(PROFILE_CORE)
@Service
public class AssessmentDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentDashboardService.class);

    private final ComplaintService complaintService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final RatingService ratingService;

    private final ResultRepository resultRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    public AssessmentDashboardService(ComplaintService complaintService, ProgrammingExerciseRepository programmingExerciseRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository, ExampleSubmissionRepository exampleSubmissionRepository, RatingService ratingService) {
        this.complaintService = complaintService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.ratingService = ratingService;
    }

    /**
     * Prepares the exercises for the assessment dashboard by setting the tutor participations and statistics
     * This is very slow as each iteration takes about 2.5 s
     *
     * @param exercises           exercises to be prepared for the assessment dashboard
     * @param tutorParticipations participations of the tutors
     * @param examMode            flag should be set for exam dashboard
     */
    public void generateStatisticsForExercisesForAssessmentDashboard(Set<Exercise> exercises, List<TutorParticipation> tutorParticipations, boolean examMode) {
        log.debug("generateStatisticsForExercisesForAssessmentDashboard invoked");
        // start measures performance of each individual query, start2 measures performance of one loop iteration
        long start = System.nanoTime();
        long start2 = System.nanoTime();
        long startComplete = System.nanoTime();
        Set<Exercise> programmingExerciseIds = exercises.stream().filter(exercise -> exercise instanceof ProgrammingExercise).collect(Collectors.toSet());
        Set<Exercise> nonProgrammingExerciseIds = exercises.stream().filter(exercise -> !(exercise instanceof ProgrammingExercise)).collect(Collectors.toSet());

        complaintService.calculateNrOfOpenComplaints(exercises, examMode);
        log.debug("Finished >> complaintService.calculateNrOfOpenComplaints all << in {}", TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        calculateNumberOfSubmissions(programmingExerciseIds, nonProgrammingExerciseIds, examMode);
        log.debug("Finished >> assessmentDashboardService.calculateNumberOfSubmissions all << in {}", TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        // NOTE: similar to calculateNumberOfSubmissions the number of assessments could be calculated outside the loop for a performance boost.
        // This won't be as straight forward as we have to consider correction rounds

        // parts of this loop can possibly still be extracted
        for (Exercise exercise : exercises) {
            DueDateStat totalNumberOfAssessments;

            if (exercise instanceof ProgrammingExercise) {
                totalNumberOfAssessments = new DueDateStat(programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted(exercise.getId()), 0L);
                log.debug("Finished >> programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted << call for exercise {} in {}", exercise.getId(),
                        TimeLogUtil.formatDurationFrom(start));
            }
            else {
                totalNumberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exercise.getId());
                log.debug("Finished >> resultRepository.countNumberOfFinishedAssessmentsForExercise << call for exercise {} in {}", exercise.getId(),
                        TimeLogUtil.formatDurationFrom(start));
            }
            start = System.nanoTime();

            final DueDateStat[] numberOfAssessmentsOfCorrectionRounds;
            if (examMode) {
                // set number of corrections specific to each correction round
                int numberOfCorrectionRounds = exercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam();
                numberOfAssessmentsOfCorrectionRounds = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds);
                log.debug("Finished >> resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds << call for exercise {} in {}", exercise.getId(),
                        TimeLogUtil.formatDurationFrom(start));
            }
            else {
                // no examMode here, so correction rounds defaults to 1 and is the same as totalNumberOfAssessments
                numberOfAssessmentsOfCorrectionRounds = new DueDateStat[] { totalNumberOfAssessments };
            }

            exercise.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);
            // numberOfAssessmentsOfCorrectionRounds can be length 0 for test exams
            if (numberOfAssessmentsOfCorrectionRounds.length > 0) {
                exercise.setTotalNumberOfAssessments(numberOfAssessmentsOfCorrectionRounds[0]);
            }

            start = System.nanoTime();
            Set<ExampleSubmission> exampleSubmissions = exampleSubmissionRepository.findAllWithResultByExerciseId(exercise.getId());

            log.debug("Finished >> exampleSubmissionRepository.findAllWithResultByExerciseId << call for course {} in {}", exercise.getId(), TimeLogUtil.formatDurationFrom(start));
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

            var exerciseRating = ratingService.averageRatingByExerciseId(exercise.getId());
            exercise.setAverageRating(exerciseRating.averageRating());
            exercise.setNumberOfRatings(exerciseRating.numberOfRatings());

            log.debug("Finished >> assessmentDashboardLoopIteration << call for exercise {} in {}", exercise.getId(), TimeLogUtil.formatDurationFrom(start2));
        }
        log.debug("Finished >> generateStatisticsForExercisesForAssessmentDashboard << call in {}", TimeLogUtil.formatDurationFrom(startComplete));
    }

    /**
     * This method fetches and stores the number of submissions for each exercise.
     *
     * @param programmingExercises    - the programming-exercises, for which the number of submissions should be fetched
     * @param nonProgrammingExercises - the exercises, which are not programming-exercises, for which the number of submissions should be fetched
     * @param examMode                - if the exercises are part of an exam
     */
    private void calculateNumberOfSubmissions(Set<Exercise> programmingExercises, Set<Exercise> nonProgrammingExercises, boolean examMode) {
        final List<ExerciseMapEntry> programmingSubmissionsCounts;
        final List<ExerciseMapEntry> submissionCounts;
        final List<ExerciseMapEntry> lateSubmissionCounts;
        Set<Long> programmingExerciseIds = programmingExercises.stream().map(Exercise::getId).collect(Collectors.toSet());
        Set<Long> nonProgrammingExerciseIds = nonProgrammingExercises.stream().map(Exercise::getId).collect(Collectors.toSet());

        // for all programming exercises and all non-programming-exercises we fetch the number of submissions here. The returned value comes in form of a list,
        // which has ExerciseMapEntries. With those for each individual exercise the number of submissions can be set.
        programmingSubmissionsCounts = programmingExerciseRepository.countSubmissionsByExerciseIdsSubmittedIgnoreTestRun(programmingExerciseIds);
        submissionCounts = submissionRepository.countByExerciseIdsSubmittedBeforeDueDateIgnoreTestRuns(nonProgrammingExerciseIds);
        if (examMode) {
            lateSubmissionCounts = new ArrayList<>();
        }
        else {
            lateSubmissionCounts = submissionRepository.countByExerciseIdsSubmittedAfterDueDate(nonProgrammingExerciseIds);
        }
        // convert the data from the queries
        var programmingSubmissionMap = programmingSubmissionsCounts.stream().collect(Collectors.toMap(ExerciseMapEntry::exerciseId, ExerciseMapEntry::value));
        var submissionMap = submissionCounts.stream().collect(Collectors.toMap(ExerciseMapEntry::exerciseId, ExerciseMapEntry::value));
        var lateSubmissionMap = lateSubmissionCounts.stream().collect(Collectors.toMap(ExerciseMapEntry::exerciseId, ExerciseMapEntry::value));

        // set the number of submissions for the exercises
        programmingExercises.forEach(exercise -> exercise.setNumberOfSubmissions(new DueDateStat(programmingSubmissionMap.getOrDefault(exercise.getId(), 0L), 0L)));
        nonProgrammingExercises.forEach(exercise -> exercise
                .setNumberOfSubmissions(new DueDateStat(submissionMap.getOrDefault(exercise.getId(), 0L), lateSubmissionMap.getOrDefault(exercise.getId(), 0L))));
    }
}
