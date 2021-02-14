package de.tum.in.www1.artemis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
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
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.dto.ExamChecklistDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.tum.in.www1.artemis.domain.Authority.ADMIN_AUTHORITY;

/**
 * Service Implementation for managing Course.
 */
@Service
public class ExamService {

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private CourseService courseService;

    private StudentExamService studentExamService;

    private final UserService userService;

    private final ExamRepository examRepository;

    private final ExerciseService exerciseService;

    private final StudentExamRepository studentExamRepository;

    private final ParticipationService participationService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final QuizExerciseService quizExerciseService;

    private final ExamQuizService examQuizService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final AuditEventRepository auditEventRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ResultService resultService;

    private final SubmissionRepository submissionRepository;

    public ExamService(ExamRepository examRepository, StudentExamRepository studentExamRepository, UserService userService, ParticipationService participationService,
            ProgrammingExerciseService programmingExerciseService, ExamQuizService examQuizService, ExerciseService exerciseService,
            InstanceMessageSendService instanceMessageSendService, QuizExerciseService quizExerciseService, AuditEventRepository auditEventRepository,
            StudentParticipationRepository studentParticipationRepository, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            ResultService resultService, SubmissionRepository submissionRepository) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.userService = userService;
        this.participationService = participationService;
        this.programmingExerciseService = programmingExerciseService;
        this.examQuizService = examQuizService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.exerciseService = exerciseService;
        this.quizExerciseService = quizExerciseService;
        this.auditEventRepository = auditEventRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.resultService = resultService;
        this.submissionRepository = submissionRepository;
    }

    @Autowired
    // break the dependency cycle
    public void setCourseService(CourseService courseService) {
        this.courseService = courseService;
    }

    @Autowired
    // break the dependency cycle
    public void setStudentExamService(StudentExamService studentExamService) {
        this.studentExamService = studentExamService;
    }

    /**
     * Save an exam.
     *
     * @param exam the entity to save
     * @return the persisted entity
     */
    public Exam save(Exam exam) {
        log.debug("Request to save exam : {}", exam);
        return examRepository.save(exam);
    }

    /**
     * Get one exam by id.
     *
     * @param examId the id of the entity
     * @return the entity
     */
    @NotNull
    public Exam findOne(Long examId) {
        log.debug("Request to get exam : {}", examId);
        return examRepository.findById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
    }

    /**
     * Get one exam by id with exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NotNull
    public Exam findOneWithExerciseGroups(Long examId) {
        log.debug("Request to get exam with exercise groups : {}", examId);
        return examRepository.findWithExerciseGroupsById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
    }

    /**
     * Get one exam by id with exercise groups and exercises.
     * Also fetches the template and solution participation for programming exercises and questions for quiz exercises.
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NotNull
    public Exam findOneWithExerciseGroupsAndExercises(Long examId) {
        log.debug("Request to get exam with exercise groups : {}", examId);
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    ProgrammingExercise exerciseWithTemplateAndSolutionParticipation = programmingExerciseService
                            .findWithTemplateAndSolutionParticipationWithResultsById(exercise.getId());
                    ((ProgrammingExercise) exercise).setTemplateParticipation(exerciseWithTemplateAndSolutionParticipation.getTemplateParticipation());
                    ((ProgrammingExercise) exercise).setSolutionParticipation(exerciseWithTemplateAndSolutionParticipation.getSolutionParticipation());
                }
                if (exercise instanceof QuizExercise) {
                    QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exercise.getId());
                    ((QuizExercise) exercise).setQuizQuestions(quizExercise.getQuizQuestions());
                }
            }
        }
        return exam;
    }

    /**
     * Get one exam by id with registered users.
     *
     * @param examId the id of the entity
     * @return the exam with registered users
     */
    @NotNull
    public Exam findOneWithRegisteredUsers(Long examId) {
        log.debug("Request to get exam with registered users : {}", examId);
        return examRepository.findWithRegisteredUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
    }

    /**
     * Get one exam by id with registered users and exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with registered users and exercise groups
     */
    @NotNull
    public Exam findOneWithRegisteredUsersAndExerciseGroupsAndExercises(Long examId) {
        log.debug("Request to get exam with registered users and registered students : {}", examId);
        return examRepository.findWithRegisteredUsersAndExerciseGroupsAndExercisesById(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
    }

    /**
     * Get all exams for the given course.
     *
     * @param courseId the id of the course
     * @return the list of all exams
     */
    public List<Exam> findAllByCourseId(Long courseId) {
        log.debug("REST request to get all exams for Course : {}", courseId);
        return examRepository.findByCourseId(courseId);
    }

    /**
     * Get all exams that are held today and/or in the future
     * (does not return exams belonging to test courses).
     *
     * @return the list of all exams
     */
    public List<Exam> findAllCurrentAndUpcomingExams() {
        log.debug("REST request to get all upcoming exams");
        return examRepository.findAllByStartDateGreaterThanEqual(ZonedDateTime.now());
    }

    /**
     * Get the exam of a course with exercise groups and student exams
     *
     * @param examId {Long} The courseId of the course which contains the exam
     * @return The exam
     */
    public Exam findOneWithExercisesGroupsAndStudentExamsByExamId(Long examId) {
        log.debug("REST request to get the exam with student exams and exercise groups for Id : {}", examId);
        return examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
    }

    /**
     * Fetches the exam using {@link #findOneWithExercisesGroupsAndStudentExamsByExamId} which eagerly loads all required elements and deletes all elements associated with the
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
        User user = userService.getUser();
        Exam exam = findOneWithExercisesGroupsAndStudentExamsByExamId(examId);
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
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

        List<StudentParticipation> studentParticipations = participationService.findByExamIdWithSubmissionRelevantResult(examId); // without test run participations

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
     * Add multiple users to the students of the exam so that they can access the exam
     * The passed list of UserDTOs must include the registration number (the other entries are currently ignored and can be left out)
     * Note: registration based on other user attributes (e.g. email, name, login) is currently NOT supported
     * <p>
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @param studentDTOs   the list of students (with at least registration number) who should get access to the exam
     * @return the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    public List<StudentDTO> registerStudentsForExam(Long courseId, Long examId, List<StudentDTO> studentDTOs) {
        var course = courseService.findOne(courseId);
        var exam = findOneWithRegisteredUsers(examId);
        List<StudentDTO> notFoundStudentsDTOs = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var registrationNumber = studentDto.getRegistrationNumber();
            var login = studentDto.getLogin();
            try {
                // 1) we use the registration number and try to find the student in the Artemis user database
                var optionalStudent = userService.findUserWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
                if (optionalStudent.isPresent()) {
                    var student = optionalStudent.get();
                    // we only need to add the student to the course group, if the student is not yet part of it, otherwise the student cannot access the exam (within the
                    // course)
                    if (!student.getGroups().contains(course.getStudentGroupName())) {
                        userService.addUserToGroup(student, course.getStudentGroupName());
                    }
                    exam.addRegisteredUser(student);
                    continue;
                }

                // 2) if we cannot find the student, we use the registration number and try to find the student in the (TUM) LDAP, create it in the Artemis DB and in a
                // potential
                // external user management system
                optionalStudent = userService.createUserFromLdap(registrationNumber);
                if (optionalStudent.isPresent()) {
                    var student = optionalStudent.get();
                    // the newly created student needs to get the rights to access the course, otherwise the student cannot access the exam (within the course)
                    userService.addUserToGroup(student, course.getStudentGroupName());
                    exam.addRegisteredUser(student);
                    continue;
                }

                // 3) if we cannot find the user in the (TUM) LDAP or the registration number was not set properly, try again using the login
                optionalStudent = userService.findUserWithGroupsAndAuthoritiesByLogin(login);
                if (optionalStudent.isPresent()) {
                    var student = optionalStudent.get();
                    // the newly created student needs to get the rights to access the course, otherwise the student cannot access the exam (within the course)
                    userService.addUserToGroup(student, course.getStudentGroupName());
                    exam.addRegisteredUser(student);
                    continue;
                }

                log.warn("User with registration number '" + registrationNumber + "' and login '" + login + "' not found in Artemis user database nor found in (TUM) LDAP");
            }
            catch (Exception ex) {
                log.warn("Error while processing user with registration number " + registrationNumber + ": " + ex.getMessage(), ex);
            }

            notFoundStudentsDTOs.add(studentDto);
        }
        examRepository.save(exam);

        try {
            User currentUser = userService.getUserWithGroupsAndAuthorities();
            Map<String, Object> userData = new HashMap<>();
            userData.put("exam", exam.getTitle());
            for (var i = 0; i < studentDTOs.size(); i++) {
                var studentDTO = studentDTOs.get(i);
                userData.put("student" + i, studentDTO.toDatabaseString());
            }
            AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, userData);
            auditEventRepository.add(auditEvent);
            log.info("User " + currentUser.getLogin() + " has added multiple users " + studentDTOs + " to the exam " + exam.getTitle() + " with id " + exam.getId());
        }
        catch (Exception ex) {
            log.warn("Could not add audit event to audit log", ex);
        }

        return notFoundStudentsDTOs;
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
        ExamChecklistDTO examChecklistDTO = new ExamChecklistDTO();

        // set number of student exams that have been generated
        long numberOfGeneratedStudentExams = examRepository.countGeneratedStudentExamsByExamWithoutTestruns(exam.getId());
        examChecklistDTO.setNumberOfGeneratedStudentExams(numberOfGeneratedStudentExams);

        // set number of test runs
        long numberOfTestRuns = studentExamRepository.countTestRunsByExamId(exam.getId());
        examChecklistDTO.setNumberOfTestRuns(numberOfTestRuns);

        List<Long> numberOfComplaintsOpenByExercise = new ArrayList<>();
        List<Long> numberOfComplaintResponsesByExercise = new ArrayList<>();
        List<DueDateStat[]> numberOfAssessmentsFinishedOfCorrectionRoundsByExercise = new ArrayList<>();
        List<Long> numberOfSubmissionsByExercise = new ArrayList<>();
        List<Long> numberOfParticipationsGeneratedByExercise = new ArrayList<>();

        exam.getExerciseGroups().forEach(exerciseGroup -> {
            exerciseGroup.getExercises().forEach(exercise -> {
                // number of complaints open
                numberOfComplaintsOpenByExercise
                        .add(complaintRepository.countByResultParticipationExerciseIdAndComplaintTypeIgnoreTestRuns(exercise.getId(), ComplaintType.COMPLAINT));

                // number of complaints finished
                numberOfComplaintResponsesByExercise.add(complaintResponseRepository
                        .countByComplaint_Result_Participation_Exercise_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(exercise.getId(), ComplaintType.COMPLAINT));

                // number of assessments done
                numberOfAssessmentsFinishedOfCorrectionRoundsByExercise
                        .add(resultService.countNumberOfFinishedAssessmentsForExerciseForCorrectionRound(exercise, exam.getNumberOfCorrectionRoundsInExam()));

                if (exercise instanceof ProgrammingExercise) {
                    numberOfSubmissionsByExercise.add(programmingExerciseService.countSubmissionsByExerciseIdSubmitted(exercise.getId(), true));
                }
                else {
                    numberOfSubmissionsByExercise.add(submissionRepository.countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(exercise.getId()));
                }

                numberOfParticipationsGeneratedByExercise.add(studentParticipationRepository.countParticipationsIgnoreTestRunsByExerciseId(exercise.getId()));

            });
        });

        long totalNumberOfComplaints = 0;
        long totalNumberOfComplaintResponse = 0;
        Long[] totalNumberOfAssessmentsFinished = new Long[exam.getNumberOfCorrectionRoundsInExam()];
        long totalNumberOfParticipationsGenerated = 0;

        for (Long numberOfParticipations : numberOfParticipationsGeneratedByExercise) {
            totalNumberOfParticipationsGenerated += numberOfParticipations != null ? numberOfParticipations : 0;
        }
        // check if all exercises have been prepared for all students;
        boolean exercisesPrepared = numberOfGeneratedStudentExams != 0 && (exam.getNumberOfExercisesInExam() * numberOfGeneratedStudentExams) == totalNumberOfParticipationsGenerated;
        examChecklistDTO.setAllExamExercisesAllStudentsPrepared(exercisesPrepared);

        for (DueDateStat[] dateStats : numberOfAssessmentsFinishedOfCorrectionRoundsByExercise) {
            for (int i = 0; i < exam.getNumberOfCorrectionRoundsInExam(); i++) {
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

        long numberOfStudentExamsStarted = studentExamRepository.countStudentExamsStartedByExamId(exam.getId());
        long numberOfStudentExamsSubmitted =  studentExamRepository.countStudentExamsSubmittedByExamId(exam.getId());

        examChecklistDTO.setNumberOfExamsStarted(numberOfStudentExamsStarted);
        examChecklistDTO.setNumberOfExamsSubmitted(numberOfStudentExamsSubmitted);

        examChecklistDTO.setNumberOfTotalExamAssessmentsFinishedByCorrectionRound(totalNumberOfAssessmentsFinished);
        examChecklistDTO.setNumberOfAllComplaints(totalNumberOfComplaints);
        examChecklistDTO.setNumberOfAllComplaintsDone(totalNumberOfComplaintResponse);
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
    private Map<Long, Integer> convertListOfCountsIntoMap(List<long[]> examIdAndRegisteredUsersCountPairs) {
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
     * Starts all the exercises of all the student exams of an exam
     *
     * @param examId exam to which the student exams belong
     * @return number of generated Participations
     */
    public int startExercises(Long examId) {

        var exam = examRepository.findWithStudentExamsExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

        var studentExams = exam.getStudentExams();
        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());
        executeInParallel(() -> studentExams.parallelStream().forEach(studentExam -> setUpExerciseParticipationsAndSubmissions(generatedParticipations, studentExam)));
        return generatedParticipations.size();
    }

    /**
     * Sets up the participations and submissions for all the exercises of the student exam.
     *
     * @param generatedParticipations List of generatedParticipations
     * @param studentExam             The studentExam
     */
    public void setUpExerciseParticipationsAndSubmissions(List<StudentParticipation> generatedParticipations, StudentExam studentExam) {
        User student = studentExam.getUser();

        for (Exercise exercise : studentExam.getExercises()) {
            SecurityUtils.setAuthorizationObject();
            // NOTE: it is not ideal to invoke the next line several times (e.g. 2000 student exams with 10 exercises would lead to 20.000 database calls to find a participation).
            // One optimization could be that we load all participations per exercise once (or per exercise) into a large list (10 * 2000 = 20.000 participations) and then check if
            // those participations exist in Java, however this might lead to memory issues and might be more difficult to program (and more difficult to understand)
            // TODO: directly check in the database if the entry exists for the student, exercise and InitializationState.INITIALIZED
            var studentParticipations = participationService.findByExerciseAndStudentId(exercise, student.getId());
            // we start the exercise if no participation was found that was already fully initialized
            if (studentParticipations.stream().noneMatch(studentParticipation -> studentParticipation.getParticipant().equals(student)
                    && studentParticipation.getInitializationState() != null && studentParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
                try {
                    if (exercise instanceof ProgrammingExercise) {
                        // TODO: we should try to move this out of the for-loop into the method which calls this method.
                        // Load lazy property
                        final var programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exercise.getId());
                        ((ProgrammingExercise) exercise).setTemplateParticipation(programmingExercise.getTemplateParticipation());
                    }
                    // this will also create initial (empty) submissions for quiz, text, modeling and file upload
                    var participation = participationService.startExercise(exercise, student, true);
                    generatedParticipations.add(participation);
                }
                catch (Exception ex) {
                    log.warn("Start exercise for student exam {} and exercise {} and student {} failed with exception: {}", studentExam.getId(), exercise.getId(), student.getId(),
                            ex.getMessage(), ex);
                }
            }
        }
    }

    private void executeInParallel(Runnable task) {
        final int numberOfParallelThreads = 10;
        ForkJoinPool forkJoinPool = new ForkJoinPool(numberOfParallelThreads);
        Future<?> future = forkJoinPool.submit(task);
        // Wait for the operation to complete
        try {
            future.get();
        }
        catch (InterruptedException e) {
            log.error("Execute in parallel got interrupted while waiting for task to complete", e);
        }
        catch (ExecutionException e) {
            log.error("Execute in parallel failed, an exception was thrown", e.getCause());
        }
        finally {
            forkJoinPool.shutdown();
        }
    }

    /**
     * Evaluates all the quiz exercises of an exam
     *
     * @param examId id of the exam for which the quiz exercises should be evaluated
     * @return number of evaluated exercises
     */
    public Integer evaluateQuizExercises(Long examId) {
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

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
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

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
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

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
     * Returns if the exam is over by checking if the latest individual exam end date plus grace period has passed.
     * See {@link ExamService#getLatestIndividualExamEndDate}
     * <p>
     *
     * @param examId the id of the exam
     * @return true if the exam is over and the students cannot submit anymore
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public boolean isExamOver(Long examId) {
        return isExamOver(findOne(examId));
    }

    /**
     * Returns if the exam is over by checking if the latest individual exam end date plus grace period has passed.
     * See {@link ExamService#getLatestIndividualExamEndDate}
     * <p>
     *
     * @param exam the exam
     * @return true if the exam is over and the students cannot submit anymore
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public boolean isExamOver(Exam exam) {
        var now = ZonedDateTime.now();
        return getLatestIndividualExamEndDate(exam).plusSeconds(exam.getGracePeriod()).isBefore(now);
    }

    /**
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param examId the id of the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public ZonedDateTime getLatestIndividualExamEndDate(Long examId) {
        return getLatestIndividualExamEndDate(findOne(examId));
    }

    /**
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param exam the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     */
    public ZonedDateTime getLatestIndividualExamEndDate(Exam exam) {
        if (exam.getStartDate() == null) {
            return null;
        }
        var maxWorkingTime = studentExamRepository.findMaxWorkingTimeByExamId(exam.getId());
        return maxWorkingTime.map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).orElse(exam.getEndDate());
    }

    /**
     * Returns all individual exam end dates as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, an empty set returned.
     *
     * @param examId the id of the exam
     * @return a set of all end dates. May return an empty set, if the exam has no start/end date or student exams cannot be found.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Long examId) {
        return getAllIndividualExamEndDates(findOne(examId));
    }

    /**
     * Returns all individual exam end dates as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, an empty set returned.
     *
     * @param exam the exam
     * @return a set of all end dates. May return an empty set, if the exam has no start/end date or student exams cannot be found.
     */
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Exam exam) {
        if (exam.getStartDate() == null) {
            return null;
        }
        var workingTimes = studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId());
        return workingTimes.stream().map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).collect(Collectors.toSet());
    }

    /**
     * Returns <code>true</code> if the current user is registered for the exam
     *
     * @param examId the id of the exam
     * @return <code>true</code> if the user if registered for the exam, false if this is not the case or the exam does not exist
     */
    public boolean isCurrentUserRegisteredForExam(Long examId) {
        return isUserRegisteredForExam(examId, userService.getUser().getId());
    }

    /**
     * Returns <code>true</code> if the user with the given id is registered for the exam
     *
     * @param examId the id of the exam
     * @param userId the id of the user to check
     * @return <code>true</code> if the user if registered for the exam, false if this is not the case or the exam does not exist
     */
    public boolean isUserRegisteredForExam(Long examId, Long userId) {
        return examRepository.isUserRegisteredForExam(examId, userId);
    }

    /**
     * Registers student to the exam. In order to do this,  we add the user the the course group, because the user only has access to the exam of a course if the student also has access to the course of the exam.
     * We only need to add the user to the course group, if the student is not yet part of it, otherwise the student cannot access the exam (within the course).
     *
     * @param course  the course containing the exam
     * @param exam    the exam for which we want to register a student
     * @param student the student to be registered to the exam
     */
    public void registerStudentToExam(Course course, Exam exam, User student) {
        exam.addRegisteredUser(student);

        if (!student.getGroups().contains(course.getStudentGroupName())) {
            userService.addUserToGroup(student, course.getStudentGroupName());
        }
        examRepository.save(exam);

        User currentUser = userService.getUserWithGroupsAndAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, "exam=" + exam.getTitle(), "student=" + student.getLogin());
        auditEventRepository.add(auditEvent);
        log.info("User " + currentUser.getLogin() + " has added user " + student.getLogin() + " to the exam " + exam.getTitle() + " with id " + exam.getId());
    }

    /**
     *
     * @param examId the exam for which a student should be unregistered
     * @param deleteParticipationsAndSubmission whether the participations and submissions of the student should be deleted
     * @param student the user object that should be unregistered
     */
    public void unregisterStudentFromExam(Long examId, boolean deleteParticipationsAndSubmission, User student) {
        var exam = findOneWithRegisteredUsers(examId);
        exam.removeRegisteredUser(student);

        // Note: we intentionally do not remove the user from the course, because the student might just have "unregistered" from the exam, but should
        // still have access to the course.
        examRepository.save(exam);

        // The student exam might already be generated, then we need to delete it
        Optional<StudentExam> optionalStudentExam = studentExamService.findOneWithExercisesByUserIdAndExamIdOptional(student.getId(), exam.getId());
        if (optionalStudentExam.isPresent()) {
            StudentExam studentExam = optionalStudentExam.get();

            // Optionally delete participations and submissions
            if (deleteParticipationsAndSubmission) {
                List<StudentParticipation> participations = participationService.findByStudentExamWithEagerSubmissionsResult(studentExam);
                for (var participation : participations) {
                    participationService.delete(participation.getId(), true, true);
                }
            }

            // Delete the student exam
            studentExamService.deleteStudentExam(studentExam.getId());
        }

        User currentUser = userService.getUserWithGroupsAndAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.REMOVE_USER_FROM_EXAM, "exam=" + exam.getTitle(), "user=" + student.getLogin());
        auditEventRepository.add(auditEvent);
        log.info("User " + currentUser.getLogin() + " has removed user " + student.getLogin() + " from the exam " + exam.getTitle() + " with id " + exam.getId()
                + ". This also deleted a potentially existing student exam with all its participations and submissions.");
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
    };

    /**
     * Adds all students registered in the course to the given exam
     *
     * @param courseId Id of the course
     * @param examId Id of the exam
     */
    public void addAllStudentsOfCourseToExam(Long courseId, Long examId) {
        Course course = courseService.findOne(courseId);
        var students = userService.getStudents(course);
        var examOpt = examRepository.findWithRegisteredUsersById(examId);

        if (examOpt.isPresent()) {
            Exam exam = examOpt.get();
            students.forEach(student -> {
                if (!exam.getRegisteredUsers().contains(student) && !student.getAuthorities().contains(ADMIN_AUTHORITY)
                        && !student.getGroups().contains(course.getInstructorGroupName())) {
                    exam.addRegisteredUser(student);
                }
            });
            examRepository.save(exam);
        }

    }
}
