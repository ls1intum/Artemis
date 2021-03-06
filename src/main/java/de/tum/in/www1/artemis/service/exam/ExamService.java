package de.tum.in.www1.artemis.service.exam;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.TutorLeaderboardService;
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

    private final SubmissionService submissionService;

    public ExamService(ExamRepository examRepository, StudentExamRepository studentExamRepository, ExamQuizService examQuizService, ExerciseService exerciseService,
            InstanceMessageSendService instanceMessageSendService, TutorLeaderboardService tutorLeaderboardService, AuditEventRepository auditEventRepository,
            StudentParticipationRepository studentParticipationRepository, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository, QuizExerciseRepository quizExerciseRepository,
            ResultRepository resultRepository, SubmissionRepository submissionRepository, SubmissionService submissionService) {
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
        this.submissionService = submissionService;
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
     * </ul>
     * Note: StudentExams and ExerciseGroups are not explicitly deleted as the delete operation of the exam is cascaded by the database.
     *
     * @param examId the ID of the exam to be deleted
     */
    public void delete(@NotNull long examId) {
        User user = userRepository.getUser();
        Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        log.info("User " + user.getLogin() + " has requested to delete the exam {}", exam.getTitle());
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);

        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup != null) {
                for (Exercise exercise : exerciseGroup.getExercises()) {
                    exerciseService.delete(exercise.getId(), true, true);
                }
            }
        }
        examRepository.deleteById(exam.getId());
    }

    /**
     * Filters the visible exams (excluding the ones that are not visible yet)
     *
     * @param exams a set of exams (e.g. the ones of a course)
     * @return only the visible exams
     */
    public Set<Exam> filterVisibleExams(Set<Exam> exams) {
        return exams.stream().filter(exam -> Boolean.TRUE.equals(exam.isVisibleToStudents())).collect(Collectors.toSet());
    }

    /**
     * Puts students, result and exerciseGroups together for ExamScoresDTO
     *
     * @param examId the id of the exam
     * @return return ExamScoresDTO with students, scores and exerciseGroups for exam
     */
    public ExamScoresDTO getExamScore(Long examId) {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        List<StudentParticipation> studentParticipations = studentParticipationRepository.findByExamIdWithSubmissionRelevantResult(examId); // without test run participations

        // Adding exam information to DTO
        ExamScoresDTO scores = new ExamScoresDTO(exam.getId(), exam.getTitle(), exam.getMaxPoints());

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
            for (StudentParticipation studentParticipation : participationsOfStudent) {
                Exercise exercise = studentParticipation.getExercise();

                // Relevant Result is already calculated
                if (studentParticipation.getResults() != null && !studentParticipation.getResults().isEmpty()) {
                    Result relevantResult = studentParticipation.getResults().iterator().next();
                    double achievedPoints = relevantResult.getScore() / 100.0 * exercise.getMaxPoints();

                    // points earned in NOT_INCLUDED exercises do not count towards the students result in the exam
                    if (!exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                        studentResult.overallPointsAchieved += Math.round(achievedPoints * 10) / 10.0;
                    }

                    // Check whether the student attempted to solve the exercise
                    boolean hasNonEmptySubmission = hasNonEmptySubmission(studentParticipation.getSubmissions(), exercise, objectMapper);
                    studentResult.exerciseGroupIdToExerciseResult.put(exercise.getExerciseGroup().getId(), new ExamScoresDTO.ExerciseResult(exercise.getId(), exercise.getTitle(),
                            exercise.getMaxPoints(), relevantResult.getScore(), achievedPoints, hasNonEmptySubmission));
                }

            }

            if (scores.maxPoints != null) {
                studentResult.overallScoreAchieved = (studentResult.overallPointsAchieved / scores.maxPoints) * 100.0;
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
     * Generates the student exams randomly based on the exam configuration and the exercise groups
     *
     * @param examWithRegisteredUsersAndExerciseGroupsAndExercises the exam with registered users, exerciseGroups and exercises loaded
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateStudentExams(final Exam examWithRegisteredUsersAndExerciseGroupsAndExercises) {
        final var examWithExistingStudentExams = findWithStudentExams(examWithRegisteredUsersAndExerciseGroupsAndExercises.getId());
        // https://jira.spring.io/browse/DATAJPA-1367 deleteInBatch does not work, because it does not cascade the deletion of existing exam sessions, therefore use deleteAll
        studentExamRepository.deleteAll(examWithExistingStudentExams.getStudentExams());

        List<ExerciseGroup> exerciseGroups = examWithRegisteredUsersAndExerciseGroupsAndExercises.getExerciseGroups();
        long numberOfOptionalExercises = examWithRegisteredUsersAndExerciseGroupsAndExercises.getNumberOfExercisesInExam()
                - exerciseGroups.stream().filter(ExerciseGroup::getIsMandatory).count();

        // StudentExams are saved in the called method
        return createRandomStudentExams(examWithRegisteredUsersAndExerciseGroupsAndExercises, examWithRegisteredUsersAndExerciseGroupsAndExercises.getRegisteredUsers(),
                numberOfOptionalExercises);
    }

    /**
     * Generates the missing student exams randomly based on the exam configuration and the exercise groups.
     * The difference between all registered users and the users who already have an individual exam
     * is the set of users for which student exams will be created.
     *
     * @param examWithRegisteredUsersAndExerciseGroupsAndExercises exam with registered users, exerciseGroups, and Exercises loaded
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateMissingStudentExams(Exam examWithRegisteredUsersAndExerciseGroupsAndExercises) {
        long numberOfOptionalExercises = examWithRegisteredUsersAndExerciseGroupsAndExercises.getNumberOfExercisesInExam()
                - examWithRegisteredUsersAndExerciseGroupsAndExercises.getExerciseGroups().stream().filter(ExerciseGroup::getIsMandatory).count();

        // Get all users who already have an individual exam
        Set<User> usersWithStudentExam = studentExamRepository.findUsersWithStudentExamsForExam(examWithRegisteredUsersAndExerciseGroupsAndExercises.getId());

        // Get all registered users
        Set<User> allRegisteredUsers = examWithRegisteredUsersAndExerciseGroupsAndExercises.getRegisteredUsers();

        // Get all students who don't have an exam yet
        Set<User> missingUsers = new HashSet<>(allRegisteredUsers);
        missingUsers.removeAll(usersWithStudentExam);

        // StudentExams are saved in the called method
        return createRandomStudentExams(examWithRegisteredUsersAndExerciseGroupsAndExercises, missingUsers, numberOfOptionalExercises);
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

        // Check that the start and end date of the exam is set
        if (exam.getStartDate() == null || exam.getEndDate() == null) {
            throw new BadRequestAlertException("The start and end date must be set for the exam", "Exam", "artemisApp.exam.validation.startAndEndMustBeSet");
        }

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
        if (exam.getMaxPoints() == null) {
            throw new BadRequestAlertException("The exam max points is not set.", "Exam", "artemisApp.exam.validation.maxPointsNotSet");
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
     * Generates random exams for each user in the given users set and saves them.
     *
     * @param exam                      exam for which the individual student exams will be generated
     * @param users                     users for which the individual exams will be generated
     * @param numberOfOptionalExercises number of optional exercises in the exam
     * @return List of StudentExams generated for the given users
     */
    private List<StudentExam> createRandomStudentExams(Exam exam, Set<User> users, long numberOfOptionalExercises) {
        List<StudentExam> studentExams = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        // Determine the default working time by computing the duration between start and end date of the exam
        Integer defaultWorkingTime = Math.toIntExact(Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds());

        // Prepare indices of mandatory and optional exercise groups to preserve order of exercise groups
        List<Integer> indicesOfMandatoryExerciseGroups = new ArrayList<>();
        List<Integer> indicesOfOptionalExerciseGroups = new ArrayList<>();
        for (int i = 0; i < exam.getExerciseGroups().size(); i++) {
            if (Boolean.TRUE.equals(exam.getExerciseGroups().get(i).getIsMandatory())) {
                indicesOfMandatoryExerciseGroups.add(i);
            }
            else {
                indicesOfOptionalExerciseGroups.add(i);
            }
        }

        for (User user : users) {
            // Create one student exam per user
            StudentExam studentExam = new StudentExam();
            studentExam.setWorkingTime(defaultWorkingTime);
            studentExam.setExam(exam);
            studentExam.setUser(user);
            studentExam.setSubmitted(false);
            studentExam.setTestRun(false);

            // Add a random exercise for each exercise group if the index of the exercise group is in assembledIndices
            List<Integer> assembledIndices = assembleIndicesListWithRandomSelection(indicesOfMandatoryExerciseGroups, indicesOfOptionalExerciseGroups, numberOfOptionalExercises);
            for (Integer index : assembledIndices) {
                // We get one random exercise from all preselected exercise groups
                studentExam.addExercise(selectRandomExercise(random, exam.getExerciseGroups().get(index)));
            }

            // Apply random exercise order
            if (Boolean.TRUE.equals(exam.getRandomizeExerciseOrder())) {
                Collections.shuffle(studentExam.getExercises());
            }

            studentExams.add(studentExam);
        }
        studentExams = studentExamRepository.saveAll(studentExams);
        return studentExams;
    }

    /**
     * Sets the transient attribute numberOfRegisteredUsers for all given exams
     *
     * @param exams Exams for which to compute and set the number of registered users
     */
    public void setNumberOfRegisteredUsersForExams(List<Exam> exams) {
        List<Long> examIds = exams.stream().map(Exam::getId).collect(Collectors.toList());
        List<long[]> examIdAndRegisteredUsersCountPairs = examRepository.countRegisteredUsersByExamIds(examIds);
        Map<Long, Integer> registeredUsersCountMap = convertListOfCountsIntoMap(examIdAndRegisteredUsersCountPairs);
        exams.forEach(exam -> exam.setNumberOfRegisteredUsers(registeredUsersCountMap.get(exam.getId()).longValue()));
    }

    /**
     * Gets all statistics for the instructor checklist regarding an exam
     *
     * @param exam the exam for which to get statistics for
     * @return a examStatisticsDTO filled with all statistics regarding the exam
     */
    public ExamChecklistDTO getStatsForChecklist(Exam exam) {
        log.info("getStatsForChecklist invoked for exam " + exam.getId());
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

            log.debug("StatsTimeLog: number of complaints open done in " + TimeLogUtil.formatDurationFrom(start) + " for exercise " + exercise.getId());
            // number of complaints finished
            numberOfComplaintResponsesByExercise.add(complaintResponseRepository
                    .countByComplaint_Result_Participation_Exercise_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(exercise.getId(), ComplaintType.COMPLAINT));

            log.debug("StatsTimeLog: number of complaints finished done in " + TimeLogUtil.formatDurationFrom(start) + " for exercise " + exercise.getId());
            // number of assessments done
            numberOfAssessmentsFinishedOfCorrectionRoundsByExercise
                    .add(resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRoundsInExam));

            log.debug("StatsTimeLog: number of assessments done in " + TimeLogUtil.formatDurationFrom(start) + " for exercise " + exercise.getId());
            // get number of all generated participations
            numberOfParticipationsGeneratedByExercise.add(studentParticipationRepository.countParticipationsIgnoreTestRunsByExerciseId(exercise.getId()));

            log.debug("StatsTimeLog: number of generated participations in " + TimeLogUtil.formatDurationFrom(start) + " for exercise " + exercise.getId());
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
                totalNumberOfAssessmentsFinished[i] += dateStats[i].getInTime();
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

        log.debug("StatsTimeLog: number of generated student exams done in " + TimeLogUtil.formatDurationFrom(start));

        // set number of test runs
        long numberOfTestRuns = studentExamRepository.countTestRunsByExamId(exam.getId());
        examChecklistDTO.setNumberOfTestRuns(numberOfTestRuns);

        log.debug("StatsTimeLog: number of test runs done in " + TimeLogUtil.formatDurationFrom(start));

        // check if all exercises have been prepared for all students;
        boolean exercisesPrepared = numberOfGeneratedStudentExams != 0
                && (exam.getNumberOfExercisesInExam() * numberOfGeneratedStudentExams) == totalNumberOfParticipationsGenerated;
        examChecklistDTO.setAllExamExercisesAllStudentsPrepared(exercisesPrepared);

        // set started and submitted exam properties
        long numberOfStudentExamsStarted = studentExamRepository.countStudentExamsStartedByExamIdIgnoreTestRuns(exam.getId());
        log.debug("StatsTimeLog: number of student exams started done in " + TimeLogUtil.formatDurationFrom(start));
        long numberOfStudentExamsSubmitted = studentExamRepository.countStudentExamsSubmittedByExamIdIgnoreTestRuns(exam.getId());
        log.debug("StatsTimeLog: number of student exams submitted done in " + TimeLogUtil.formatDurationFrom(start));
        examChecklistDTO.setNumberOfTotalParticipationsForAssessment(totalNumberOfParticipationsForAssessment);
        examChecklistDTO.setNumberOfExamsStarted(numberOfStudentExamsStarted);
        examChecklistDTO.setNumberOfExamsSubmitted(numberOfStudentExamsSubmitted);
        return examChecklistDTO;
    }

    /**
     * Finds an exam based on the id with all student exams which are not marked as test runs.
     *
     * @param examId the id of the exam
     * @return the exam with student exams loaded
     */
    public Exam findWithStudentExams(long examId) {
        Exam exam = examRepository.findWithStudentExamsById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id " + examId + " does not exist"));
        // drop all test runs and set the remaining student exams to the exam
        exam.setStudentExams(exam.getStudentExams().stream().dropWhile(StudentExam::isTestRun).collect(Collectors.toSet()));
        return exam;
    }

    /**
     * Converts List<[examId, registeredUsersCount]> into Map<examId -> registeredUsersCount>
     *
     * @param examIdAndRegisteredUsersCountPairs list of pairs (examId, registeredUsersCount)
     * @return map of exam id to registered users count
     */
    private static Map<Long, Integer> convertListOfCountsIntoMap(List<long[]> examIdAndRegisteredUsersCountPairs) {
        return examIdAndRegisteredUsersCountPairs.stream().collect(Collectors.toMap(examIdAndRegisteredUsersCountPair -> examIdAndRegisteredUsersCountPair[0], // examId
                examIdAndRegisteredUsersCountPair -> Math.toIntExact(examIdAndRegisteredUsersCountPair[1]) // registeredUsersCount
        ));
    }

    private List<Integer> assembleIndicesListWithRandomSelection(List<Integer> mandatoryIndices, List<Integer> optionalIndices, Long numberOfOptionalExercises) {
        // Add all mandatory indices
        List<Integer> indices = new ArrayList<>(mandatoryIndices);

        // Add as many optional indices as numberOfOptionalExercises
        if (numberOfOptionalExercises > 0) {
            Collections.shuffle(optionalIndices);
            indices = Stream.concat(indices.stream(), optionalIndices.stream().limit(numberOfOptionalExercises)).collect(Collectors.toList());
        }

        // Sort the indices to preserve the original order
        Collections.sort(indices);
        return indices;
    }

    private Exercise selectRandomExercise(SecureRandom random, ExerciseGroup exerciseGroup) {
        List<Exercise> exercises = new ArrayList<>(exerciseGroup.getExercises());
        int randomIndex = random.nextInt(exercises.size());
        return exercises.get(randomIndex);
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
     * Returns a set containing all exercises that are defined in the
     * specified exam.
     *
     * @param examId The id of the exam
     * @return A set containing the exercises
     */
    public Set<Exercise> getAllExercisesOfExam(long examId) {
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(examId);
        if (exam.isEmpty()) {
            return Set.of();
        }

        return exam.get().getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    /**
     * Gets a collection of useful statistics for the tutor exam-assessment-dashboard, including: - number of submissions to the course - number of
     * assessments - number of assessments assessed by the tutor - number of complaints
     * @param course    - the couse of the exam
     * @param examId    - the id of the exam to retrieve stats from
     * @return data about a exam including all exercises, plus some data for the tutor as tutor status for assessment
     */
    public StatsForInstructorDashboardDTO getStatsForExamAssessmentDashboard(Course course, Long examId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        final long numberOfSubmissions = submissionRepository.countByExamIdSubmittedSubmissionsIgnoreTestRuns(examId)
                + programmingExerciseRepository.countSubmissionsByExamIdSubmitted(examId);
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
     * Get all currently locked submissions for all users in the given exam.
     * These are all submissions for which users started, but did not yet finish the assessment.
     *
     * @param examId  - the exam id
     * @param user    - the user trying to access the locked submissions
     * @return        - list of submissions that have locked results in the exam
     */
    public List<Submission> getLockedSubmissions(Long examId, User user) {
        List<Submission> submissions = submissionRepository.getLockedSubmissionsAndResultsByExamId(examId);

        for (Submission submission : submissions) {
            submissionService.hideDetails(submission, user);
        }
        return submissions;
    }
}
