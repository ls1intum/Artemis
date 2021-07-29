package de.tum.in.www1.artemis.service.exam;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.round;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing exams.
 */
@Service
public class ExamService {

    @Value("${artemis.course-archives-path}")
    private String examArchivesDirPath;

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final UserRepository userRepository;

    private final ExerciseService exerciseService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ExamQuizService examQuizService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final AuditEventRepository auditEventRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ResultRepository resultRepository;

    private final SubmissionRepository submissionRepository;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final GitService gitService;

    private final CourseExamExportService courseExamExportService;

    private final GroupNotificationService groupNotificationService;

    private final GradingScaleRepository gradingScaleRepository;

    public ExamService(ExamRepository examRepository, StudentExamRepository studentExamRepository, ExamQuizService examQuizService, ExerciseService exerciseService,
            InstanceMessageSendService instanceMessageSendService, TutorLeaderboardService tutorLeaderboardService, AuditEventRepository auditEventRepository,
            StudentParticipationRepository studentParticipationRepository, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository, QuizExerciseRepository quizExerciseRepository,
            ResultRepository resultRepository, SubmissionRepository submissionRepository, CourseExamExportService courseExamExportService, GitService gitService,
            GroupNotificationService groupNotificationService, GradingScaleRepository gradingScaleRepository) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.examQuizService = examQuizService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.exerciseService = exerciseService;
        this.auditEventRepository = auditEventRepository;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.courseExamExportService = courseExamExportService;
        this.groupNotificationService = groupNotificationService;
        this.gitService = gitService;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    /**
     * Get one exam by id with exercise groups and exercises.
     * Also fetches the template and solution participation for programming exercises and questions for quiz exercises.
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NotNull
    public Exam findByIdWithExerciseGroupsAndExercisesElseThrow(Long examId) {
        log.debug("Request to get exam with exercise groups : {}", examId);
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    ProgrammingExercise exerciseWithTemplateAndSolutionParticipation = programmingExerciseRepository
                            .findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(exercise.getId());
                    ((ProgrammingExercise) exercise).setTemplateParticipation(exerciseWithTemplateAndSolutionParticipation.getTemplateParticipation());
                    ((ProgrammingExercise) exercise).setSolutionParticipation(exerciseWithTemplateAndSolutionParticipation.getSolutionParticipation());
                }
                if (exercise instanceof QuizExercise) {
                    QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId());
                    ((QuizExercise) exercise).setQuizQuestions(quizExercise.getQuizQuestions());
                }
            }
        }
        return exam;
    }

    /**
     * Fetches the exam and eagerly loads all required elements and deletes all elements associated with the
     * exam including:
     * <ul>
     *     <li>The Exam</li>
     *     <li>All ExerciseGroups</li>
     *     <li>All Exercises including:
     *     Submissions, Participations, Results, Repositories and build plans, see {@link ExerciseService#delete}</li>
     *     <li>All StudentExams</li>
     *     <li>The exam Grading Scale if such exists</li>
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

        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup != null) {
                for (Exercise exercise : exerciseGroup.getExercises()) {
                    exerciseService.delete(exercise.getId(), true, true);
                }
            }
        }
        deleteGradingScaleOfExam(exam);
        examRepository.deleteById(exam.getId());
    }

    private void deleteGradingScaleOfExam(Exam exam) {
        // delete exam grading scale if it exists
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamId(exam.getId());
        gradingScale.ifPresent(gradingScaleRepository::delete);
    }

    /**
     * Puts students, result and exerciseGroups together for ExamScoresDTO
     *
     * @param examId the id of the exam
     * @return return ExamScoresDTO with students, scores and exerciseGroups for exam
     */
    public ExamScoresDTO calculateExamScores(Long examId) {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        List<StudentParticipation> studentParticipations = studentParticipationRepository.findByExamIdWithSubmissionRelevantResult(examId); // without test run participations

        // Adding exam information to DTO
        ExamScoresDTO scores = new ExamScoresDTO(exam.getId(), exam.getTitle(), exam.getMaxPoints());

        // setting multiplicity of correction rounds
        scores.hasSecondCorrectionAndStarted = false;

        // Counts how many participants each exercise has
        Map<Long, Long> exerciseIdToNumberParticipations = studentParticipations.stream()
                .collect(Collectors.groupingBy(studentParticipation -> studentParticipation.getExercise().getId(), Collectors.counting()));

        // Adding exercise group information to DTO
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            // Find the maximum points for this exercise group
            OptionalDouble optionalMaxPointsGroup = exerciseGroup.getExercises().stream().mapToDouble(Exercise::getMaxPoints).max();
            Double maxPointsGroup = optionalMaxPointsGroup.orElse(0);

            // Counter for exerciseGroup participations. Is calculated by summing up the number of exercise participations
            long numberOfExerciseGroupParticipants = 0;
            // Add information about exercise groups and exercises
            var exerciseGroupDTO = new ExamScoresDTO.ExerciseGroup(exerciseGroup.getId(), exerciseGroup.getTitle(), maxPointsGroup);
            for (Exercise exercise : exerciseGroup.getExercises()) {
                Long participantsForExercise = exerciseIdToNumberParticipations.get(exercise.getId());
                // If no participation exists for an exercise then no entry exists in the map
                if (participantsForExercise == null) {
                    participantsForExercise = 0L;
                }
                numberOfExerciseGroupParticipants += participantsForExercise;
                exerciseGroupDTO.containedExercises
                        .add(new ExamScoresDTO.ExerciseGroup.ExerciseInfo(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), participantsForExercise));
            }
            exerciseGroupDTO.numberOfParticipants = numberOfExerciseGroupParticipants;
            scores.exerciseGroups.add(exerciseGroupDTO);
        }

        // Adding registered student information to DTO
        Set<StudentExam> studentExams = studentExamRepository.findByExamId(examId); // fetched without test runs
        ObjectMapper objectMapper = new ObjectMapper();
        for (StudentExam studentExam : studentExams) {

            User user = studentExam.getUser();
            var studentResult = new ExamScoresDTO.StudentResult(user.getId(), user.getName(), user.getEmail(), user.getLogin(), user.getRegistrationNumber(),
                    studentExam.isSubmitted());

            // Adding student results information to DTO
            List<StudentParticipation> participationsOfStudent = studentParticipations.stream()
                    .filter(studentParticipation -> studentParticipation.getStudent().get().getId().equals(studentResult.userId)).collect(Collectors.toList());

            studentResult.overallPointsAchieved = 0.0;
            studentResult.overallPointsAchievedInFirstCorrection = 0.0;
            for (StudentParticipation studentParticipation : participationsOfStudent) {
                Exercise exercise = studentParticipation.getExercise();

                // Relevant Result is already calculated
                if (studentParticipation.getResults() != null && !studentParticipation.getResults().isEmpty()) {
                    Result relevantResult = studentParticipation.getResults().iterator().next();
                    // Note: It is important that we round on the individual exercise level first and then sum up.
                    // This is necessary so that the student arrives at the same overall result when doing his own recalculation.
                    // Let's assume that the student achieved 1.05 points in each of 5 exercises.
                    // In the client, these are now displayed rounded as 1.1 points.
                    // If the student adds up the displayed points, he gets a total of 5.5 points.
                    // In order to get the same total result as the student, we have to round before summing.
                    double achievedPoints = round(relevantResult.getScore() / 100.0 * exercise.getMaxPoints());

                    // points earned in NOT_INCLUDED exercises do not count towards the students result in the exam
                    if (!exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                        studentResult.overallPointsAchieved += achievedPoints;
                    }

                    // collect points of first correction, if a second correction exists
                    if (exam.getNumberOfCorrectionRoundsInExam() == 2 && !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                        Optional<Submission> latestSubmission = studentParticipation.findLatestSubmission();
                        if (latestSubmission.isPresent()) {
                            Submission submission = latestSubmission.get();
                            // Check if second correction already started
                            if (submission.getManualResults().size() > 1) {
                                if (!scores.hasSecondCorrectionAndStarted) {
                                    scores.hasSecondCorrectionAndStarted = true;
                                }
                                Result firstManualResult = submission.getFirstManualResult();
                                double achievedPointsInFirstCorrection = 0.0;
                                if (firstManualResult != null) {
                                    Double resultScore = firstManualResult.getScore();
                                    achievedPointsInFirstCorrection = resultScore != null ? round(resultScore / 100.0 * exercise.getMaxPoints()) : 0.0;
                                }
                                studentResult.overallPointsAchievedInFirstCorrection += achievedPointsInFirstCorrection;
                            }
                        }
                    }

                    // Check whether the student attempted to solve the exercise
                    boolean hasNonEmptySubmission = hasNonEmptySubmission(studentParticipation.getSubmissions(), exercise, objectMapper);
                    studentResult.exerciseGroupIdToExerciseResult.put(exercise.getExerciseGroup().getId(), new ExamScoresDTO.ExerciseResult(exercise.getId(), exercise.getTitle(),
                            exercise.getMaxPoints(), relevantResult.getScore(), achievedPoints, hasNonEmptySubmission));
                }

            }

            if (scores.maxPoints != null) {
                studentResult.overallScoreAchieved = (studentResult.overallPointsAchieved / scores.maxPoints) * 100.0;
                // Sets grading scale related properties for exam scores
                Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamId(examId);
                if (gradingScale.isPresent()) {
                    // Calculate current student grade
                    GradeStep studentGrade = gradingScaleRepository.matchPercentageToGradeStep(studentResult.overallScoreAchieved, gradingScale.get().getId());
                    studentResult.overallGrade = studentGrade.getGradeName();
                    studentResult.hasPassed = studentGrade.getIsPassingGrade();
                }
            }
            scores.studentResults.add(studentResult);
        }

        // Updating exam information in DTO
        double sumOverallPoints = scores.studentResults.stream().mapToDouble(studentResult -> studentResult.overallPointsAchieved).sum();

        int numberOfStudentResults = scores.studentResults.size();

        if (numberOfStudentResults != 0) {
            scores.averagePointsAchieved = sumOverallPoints / numberOfStudentResults;
        }

        return scores;
    }

    /**
     * Checks whether one of the submissions is not empty
     *
     * @param submissions         Submissions to check
     * @param exercise            Exercise of the submissions
     * @param jacksonObjectMapper Mapper to parse a modeling exercise model string to JSON
     * @return true if at least one submission is not empty else false
     */
    private boolean hasNonEmptySubmission(Set<Submission> submissions, Exercise exercise, ObjectMapper jacksonObjectMapper) {
        if (exercise instanceof ProgrammingExercise) {
            return submissions.stream().anyMatch(submission -> submission.getType() == SubmissionType.MANUAL);
        }
        else if (exercise instanceof FileUploadExercise) {
            FileUploadSubmission textSubmission = (FileUploadSubmission) submissions.iterator().next();
            return textSubmission.getFilePath() != null && !textSubmission.getFilePath().isEmpty();
        }
        else if (exercise instanceof TextExercise) {
            TextSubmission textSubmission = (TextSubmission) submissions.iterator().next();
            return textSubmission.getText() != null && !textSubmission.getText().isBlank();
        }
        else if (exercise instanceof ModelingExercise) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) submissions.iterator().next();
            try {
                return !modelingSubmission.isEmpty(jacksonObjectMapper);
            }
            catch (Exception e) {
                // Then the student most likely submitted something which breaks the model, if parsing fails
                return true;
            }
        }
        else if (exercise instanceof QuizExercise) {
            QuizSubmission quizSubmission = (QuizSubmission) submissions.iterator().next();
            return quizSubmission != null && !quizSubmission.getSubmittedAnswers().isEmpty();
        }
        else {
            throw new IllegalArgumentException("The exercise type of the exercise with id " + exercise.getId() + " is not supported");
        }
    }

    /**
     * Validates exercise settings.
     *
     * @param exam exam which is validated
     * @throws BadRequestAlertException an exception if the exam is not configured correctly
     */
    public void validateForStudentExamGeneration(Exam exam) throws BadRequestAlertException {
        List<ExerciseGroup> exerciseGroups = exam.getExerciseGroups();
        long numberOfExercises = exam.getNumberOfExercisesInExam() != null ? exam.getNumberOfExercisesInExam() : 0;
        long numberOfOptionalExercises = numberOfExercises - exerciseGroups.stream().filter(ExerciseGroup::getIsMandatory).count();

        // Ensure that all exercise groups have at least one exercise
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup.getExercises().isEmpty()) {
                throw new BadRequestAlertException("All exercise groups must have at least one exercise", "Exam", "artemisApp.exam.validation.atLeastOneExercisePerExerciseGroup");
            }
        }

        // Check that numberOfExercisesInExam is set
        if (exam.getNumberOfExercisesInExam() == null) {
            throw new BadRequestAlertException("The number of exercises in the exam is not set.", "Exam", "artemisApp.exam.validation.numberOfExercisesInExamNotSet");
        }

        // Check that there are enough exercise groups
        if (exam.getExerciseGroups().size() < exam.getNumberOfExercisesInExam()) {
            throw new BadRequestAlertException("The number of exercise groups is too small", "Exam", "artemisApp.exam.validation.tooFewExerciseGroups");
        }

        // Check that there are not too much mandatory exercise groups
        if (numberOfOptionalExercises < 0) {
            throw new BadRequestAlertException("The number of mandatory exercise groups is too large", "Exam", "artemisApp.exam.validation.tooManyMandatoryExerciseGroups");
        }

        // Ensure that all exercises in an exercise group have the same meaning for the exam score calculation
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            Set<IncludedInOverallScore> meaningsForScoreCalculation = exerciseGroup.getExercises().stream().map(Exercise::getIncludedInOverallScore).collect(Collectors.toSet());
            if (meaningsForScoreCalculation.size() > 1) {
                throw new BadRequestAlertException("All exercises in an exercise group must have the same meaning for the exam score", "Exam",
                        "artemisApp.exam.validation.allExercisesInExerciseGroupOfSameIncludedType");
            }
        }

        // Check that the exam max points is set
        if (exam.getMaxPoints() == 0) {
            throw new BadRequestAlertException("The exam max points can not be 0.", "Exam", "artemisApp.exam.validation.maxPointsNotSet");
        }

        // Ensure that all exercises in an exercise group have the same amount of max points and max bonus points
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            Set<Double> allMaxPoints = exerciseGroup.getExercises().stream().map(Exercise::getMaxPoints).collect(Collectors.toSet());
            Set<Double> allBonusPoints = exerciseGroup.getExercises().stream().map(Exercise::getBonusPoints).collect(Collectors.toSet());

            if (allMaxPoints.size() > 1 || allBonusPoints.size() > 1) {
                throw new BadRequestAlertException("All exercises in an exercise group need to give the same amount of points", "Exam",
                        "artemisApp.exam.validation.allExercisesInExerciseGroupGiveSameNumberOfPoints");
            }
        }

        // Ensure that the sum of all max points of mandatory exercise groups is not bigger than the max points set in the exam
        // At this point we are already sure that each exercise group has at least one exercise, all exercises in the group have the same no of points
        // and all are of the same calculation type, therefore we can just use any as representation for the group here
        Double pointsReachableByMandatoryExercises = 0.0;
        Set<ExerciseGroup> mandatoryExerciseGroups = exam.getExerciseGroups().stream().filter(ExerciseGroup::getIsMandatory).collect(Collectors.toSet());
        for (ExerciseGroup exerciseGroup : mandatoryExerciseGroups) {
            Exercise groupRepresentativeExercise = exerciseGroup.getExercises().stream().findAny().get();
            if (groupRepresentativeExercise.getIncludedInOverallScore().equals(IncludedInOverallScore.INCLUDED_COMPLETELY)) {
                pointsReachableByMandatoryExercises += groupRepresentativeExercise.getMaxPoints();
            }
        }
        if (pointsReachableByMandatoryExercises > exam.getMaxPoints()) {
            throw new BadRequestAlertException("Check that you set the exam max points correctly! The max points a student can earn in the mandatory exercise groups is too big",
                    "Exam", "artemisApp.exam.validation.tooManyMaxPoints");
        }

        // Ensure that the sum of all max points of all exercise groups is at least as big as the max points set in the exam
        Double pointsReachable = 0.0;
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            Exercise groupRepresentativeExercise = exerciseGroup.getExercises().stream().findAny().get();
            if (groupRepresentativeExercise.getIncludedInOverallScore().equals(IncludedInOverallScore.INCLUDED_COMPLETELY)) {
                pointsReachable += groupRepresentativeExercise.getMaxPoints();
            }
        }
        if (pointsReachable < exam.getMaxPoints()) {
            throw new BadRequestAlertException("Check that you set the exam max points correctly! The max points a student can earn in the exercise groups is too low", "Exam",
                    "artemisApp.exam.validation.tooFewMaxPoints");
        }
    }

    /**
     * Gets all statistics for the instructor checklist regarding an exam
     *
     * @param exam the exam for which to get statistics for
     * @return a examStatisticsDTO filled with all statistics regarding the exam
     */
    public ExamChecklistDTO getStatsForChecklist(Exam exam) {
        log.info("getStatsForChecklist invoked for exam {}", exam.getId());
        int numberOfCorrectionRoundsInExam = exam.getNumberOfCorrectionRoundsInExam();
        long start = System.nanoTime();
        ExamChecklistDTO examChecklistDTO = new ExamChecklistDTO();

        List<Long> numberOfComplaintsOpenByExercise = new ArrayList<>();
        List<Long> numberOfComplaintResponsesByExercise = new ArrayList<>();
        List<DueDateStat[]> numberOfAssessmentsFinishedOfCorrectionRoundsByExercise = new ArrayList<>();
        List<Long> numberOfParticipationsGeneratedByExercise = new ArrayList<>();
        List<Long> numberOfParticipationsForAssessmentGeneratedByExercise = new ArrayList<>();

        // loop over all exercises and retrieve all needed counts for the properties at once
        exam.getExerciseGroups().forEach(exerciseGroup -> exerciseGroup.getExercises().forEach(exercise -> {
            // number of complaints open
            numberOfComplaintsOpenByExercise.add(complaintRepository.countByResultParticipationExerciseIdAndComplaintTypeIgnoreTestRuns(exercise.getId(), ComplaintType.COMPLAINT));

            log.debug("StatsTimeLog: number of complaints open done in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
            // number of complaints finished
            numberOfComplaintResponsesByExercise
                    .add(complaintResponseRepository.countComplaintResponseByExerciseIdAndComplaintTypeAndSubmittedTimeIsNotNull(exercise.getId(), ComplaintType.COMPLAINT));

            log.debug("StatsTimeLog: number of complaints finished done in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
            // number of assessments done
            numberOfAssessmentsFinishedOfCorrectionRoundsByExercise
                    .add(resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRoundsInExam));

            log.debug("StatsTimeLog: number of assessments done in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
            // get number of all generated participations
            numberOfParticipationsGeneratedByExercise.add(studentParticipationRepository.countParticipationsIgnoreTestRunsByExerciseId(exercise.getId()));

            log.debug("StatsTimeLog: number of generated participations in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
            if (!(exercise instanceof QuizExercise || exercise.getAssessmentType() == AssessmentType.AUTOMATIC)) {
                numberOfParticipationsForAssessmentGeneratedByExercise.add(submissionRepository.countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(exercise.getId()));
            }
        }));

        long totalNumberOfComplaints = 0;
        long totalNumberOfComplaintResponse = 0;
        Long[] totalNumberOfAssessmentsFinished = new Long[numberOfCorrectionRoundsInExam];
        long totalNumberOfParticipationsGenerated = 0;
        long totalNumberOfParticipationsForAssessment = 0;

        // sum up all counts for the different properties
        for (Long numberOfParticipations : numberOfParticipationsGeneratedByExercise) {
            totalNumberOfParticipationsGenerated += numberOfParticipations != null ? numberOfParticipations : 0;
        }
        // sum up all counts for the different properties
        for (Long numberOfParticipationsForAssessment : numberOfParticipationsForAssessmentGeneratedByExercise) {
            totalNumberOfParticipationsForAssessment += numberOfParticipationsForAssessment != null ? numberOfParticipationsForAssessment : 0;
        }

        for (DueDateStat[] dateStats : numberOfAssessmentsFinishedOfCorrectionRoundsByExercise) {
            for (int i = 0; i < numberOfCorrectionRoundsInExam; i++) {
                if (totalNumberOfAssessmentsFinished[i] == null) {
                    totalNumberOfAssessmentsFinished[i] = 0L;
                }
                totalNumberOfAssessmentsFinished[i] += dateStats[i].inTime();
            }
        }
        for (Long numberOfComplaints : numberOfComplaintsOpenByExercise) {
            totalNumberOfComplaints += numberOfComplaints;
        }
        for (Long numberOfComplaintResponse : numberOfComplaintResponsesByExercise) {
            totalNumberOfComplaintResponse += numberOfComplaintResponse;
        }
        examChecklistDTO.setNumberOfTotalExamAssessmentsFinishedByCorrectionRound(totalNumberOfAssessmentsFinished);
        examChecklistDTO.setNumberOfAllComplaints(totalNumberOfComplaints);
        examChecklistDTO.setNumberOfAllComplaintsDone(totalNumberOfComplaintResponse);

        // set number of student exams that have been generated
        long numberOfGeneratedStudentExams = examRepository.countGeneratedStudentExamsByExamWithoutTestRuns(exam.getId());
        examChecklistDTO.setNumberOfGeneratedStudentExams(numberOfGeneratedStudentExams);

        log.debug("StatsTimeLog: number of generated student exams done in {}", TimeLogUtil.formatDurationFrom(start));

        // set number of test runs
        long numberOfTestRuns = studentExamRepository.countTestRunsByExamId(exam.getId());
        examChecklistDTO.setNumberOfTestRuns(numberOfTestRuns);

        log.debug("StatsTimeLog: number of test runs done in {}", TimeLogUtil.formatDurationFrom(start));

        // check if all exercises have been prepared for all students;
        boolean exercisesPrepared = numberOfGeneratedStudentExams != 0
                && (exam.getNumberOfExercisesInExam() * numberOfGeneratedStudentExams) == totalNumberOfParticipationsGenerated;
        examChecklistDTO.setAllExamExercisesAllStudentsPrepared(exercisesPrepared);

        // set started and submitted exam properties
        long numberOfStudentExamsStarted = studentExamRepository.countStudentExamsStartedByExamIdIgnoreTestRuns(exam.getId());
        log.debug("StatsTimeLog: number of student exams started done in {}", TimeLogUtil.formatDurationFrom(start));
        long numberOfStudentExamsSubmitted = studentExamRepository.countStudentExamsSubmittedByExamIdIgnoreTestRuns(exam.getId());
        log.debug("StatsTimeLog: number of student exams submitted done in {}", TimeLogUtil.formatDurationFrom(start));
        examChecklistDTO.setNumberOfTotalParticipationsForAssessment(totalNumberOfParticipationsForAssessment);
        examChecklistDTO.setNumberOfExamsStarted(numberOfStudentExamsStarted);
        examChecklistDTO.setNumberOfExamsSubmitted(numberOfStudentExamsSubmitted);
        return examChecklistDTO;
    }

    /**
     * Evaluates all the quiz exercises of an exam
     *
     * @param examId id of the exam for which the quiz exercises should be evaluated
     * @return number of evaluated exercises
     */
    public Integer evaluateQuizExercises(Long examId) {
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        // Collect all quiz exercises for the given exam
        Set<QuizExercise> quizExercises = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof QuizExercise) {
                    quizExercises.add((QuizExercise) exercise);
                }
            }
        }

        long start = System.nanoTime();
        log.info("Evaluating {} quiz exercises in exam {}", quizExercises.size(), examId);
        // Evaluate all quizzes for that exercise
        quizExercises.forEach(quiz -> examQuizService.evaluateQuizAndUpdateStatistics(quiz.getId()));
        log.info("Evaluated {} quiz exercises in exam {} in {}", quizExercises.size(), examId, TimeLogUtil.formatDurationFrom(start));

        return quizExercises.size();
    }

    /**
     * Unlocks all repositories of an exam
     *
     * @param examId id of the exam for which the repositories should be unlocked
     * @return number of exercises for which the repositories are unlocked
     */
    public Integer unlockAllRepositories(Long examId) {
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        // Collect all programming exercises for the given exam
        Set<ProgrammingExercise> programmingExercises = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    programmingExercises.add((ProgrammingExercise) exercise);
                }
            }
        }

        for (ProgrammingExercise programmingExercise : programmingExercises) {
            // Run the runnable immediately so that the repositories are unlocked as fast as possible
            instanceMessageSendService.sendUnlockAllRepositories(programmingExercise.getId());
        }

        return programmingExercises.size();
    }

    /**
     * Locks all repositories of an exam
     *
     * @param examId id of the exam for which the repositories should be locked
     * @return number of exercises for which the repositories are locked
     */
    public Integer lockAllRepositories(Long examId) {
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        // Collect all programming exercises for the given exam
        Set<ProgrammingExercise> programmingExercises = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    programmingExercises.add((ProgrammingExercise) exercise);
                }
            }
        }

        for (ProgrammingExercise programmingExercise : programmingExercises) {
            // Run the runnable immediately so that the repositories are locked as fast as possible
            instanceMessageSendService.sendLockAllRepositories(programmingExercise.getId());
        }

        return programmingExercises.size();
    }

    /**
     * Sets exam transient properties for different exercise types
     * @param exam - the exam for which we set the properties
     */
    public void setExamProperties(Exam exam) {
        exam.getExerciseGroups().forEach(exerciseGroup -> {
            exerciseGroup.getExercises().forEach(exercise -> {
                // Set transient property for quiz exam exercise if test runs exist
                if (exercise instanceof QuizExercise) {
                    exerciseService.checkTestRunsExist(exercise);
                }
            });
            // set transient number of participations for each exercise
            studentParticipationRepository.addNumberOfExamExerciseParticipations(exerciseGroup);
        });
        // set transient number of registered users
        examRepository.setNumberOfRegisteredUsersForExams(Collections.singletonList(exam));
    }

    /**
     * Gets a collection of useful statistics for the tutor exam-assessment-dashboard, including: - number of submissions to the course - number of
     * assessments - number of assessments assessed by the tutor - number of complaints
     *
     * @param course - the couse of the exam
     * @param examId - the id of the exam to retrieve stats from
     * @return data about a exam including all exercises, plus some data for the tutor as tutor status for assessment
     */
    public StatsForDashboardDTO getStatsForExamAssessmentDashboard(Course course, Long examId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        StatsForDashboardDTO stats = new StatsForDashboardDTO();

        final long numberOfSubmissions = submissionRepository.countByExamIdSubmittedSubmissionsIgnoreTestRuns(examId)
                + programmingExerciseRepository.countLegalSubmissionsByExamIdSubmitted(examId);
        stats.setNumberOfSubmissions(new DueDateStat(numberOfSubmissions, 0));

        DueDateStat[] numberOfAssessmentsOfCorrectionRounds = resultRepository.countNumberOfFinishedAssessmentsForExamForCorrectionRounds(examId,
                exam.getNumberOfCorrectionRoundsInExam());
        stats.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);

        final long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_ExerciseGroup_Exam_IdAndComplaintType(examId, ComplaintType.COMPLAINT);
        stats.setNumberOfComplaints(numberOfComplaints);

        final long numberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByUserIdAndExamId(userRepository.getUserWithGroupsAndAuthorities().getId(), examId);
        stats.setNumberOfAssessmentLocks(numberOfAssessmentLocks);

        final long totalNumberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByExamId(examId);
        stats.setTotalNumberOfAssessmentLocks(totalNumberOfAssessmentLocks);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getExamLeaderboard(course, exam);
        stats.setTutorLeaderboardEntries(leaderboardEntries);
        return stats;
    }

    /**
     * Archives the exam by creating a zip file with student submissions for
     * exercises of the exam.
     *
     * @param exam the exam to archive
     */
    @Async
    public void archiveExam(Exam exam) {
        SecurityUtils.setAuthorizationObject();

        // Archiving a course is only possible after the exam is over
        if (ZonedDateTime.now().isBefore(exam.getEndDate())) {
            return;
        }

        // This contains possible errors encountered during the archive process
        ArrayList<String> exportErrors = new ArrayList<>();

        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_STARTED, exportErrors);

        try {
            // Create exam archives directory if it doesn't exist
            Files.createDirectories(Path.of(examArchivesDirPath));
            log.info("Created the exam archives directory at {} because it didn't exist.", examArchivesDirPath);

            // Export the exam to the archives directory.
            var archivedExamPath = courseExamExportService.exportExam(exam, examArchivesDirPath, exportErrors);

            // Attach the path to the archive to the exam and save it in the database
            if (archivedExamPath.isPresent()) {
                exam.setExamArchivePath(archivedExamPath.get().getFileName().toString());
                examRepository.save(exam);
            }
            else {
                groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_FAILED, exportErrors);
                return;
            }
        }
        catch (IOException e) {
            var error = "Failed to create exam archives directory " + examArchivesDirPath + ": " + e.getMessage();
            exportErrors.add(error);
            log.info(error);
        }

        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_FINISHED, exportErrors);
    }

    /**
     * Combines the template commits of all programming exercises in the exam.
     * This is executed before the individual student exams are generated.
     *
     * @param exam - the exam which template commits should be combined
     */
    public void combineTemplateCommitsOfAllProgrammingExercisesInExam(Exam exam) {
        exam.getExerciseGroups().forEach(group -> group.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).forEach(exercise -> {
            try {
                ProgrammingExercise programmingExerciseWithTemplateParticipation = programmingExerciseRepository
                        .findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
                gitService.combineAllCommitsOfRepositoryIntoOne(programmingExerciseWithTemplateParticipation.getTemplateParticipation().getVcsRepositoryUrl());
                log.debug("Finished combination of template commits for programming exercise {}", programmingExerciseWithTemplateParticipation.toString());
            }
            catch (InterruptedException | GitAPIException e) {
                log.error("An error occurred when trying to combine template commits for exam " + exam.getId() + ".", e);
            }
        }));
    }

    /**
     * Schedules all modeling exercises
     * This is executed when exam is updated or individual working times are updated
     *
     * @param exam - the exam whose modeling exercises will be scheduled
     */
    public void scheduleModelingExercises(Exam exam) {
        // for all modeling exercises in the exam, send their ids for scheduling
        exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(exercise -> exercise instanceof ModelingExercise).map(Exercise::getId)
                .forEach(instanceMessageSendService::sendModelingExerciseSchedule);
    }
}
