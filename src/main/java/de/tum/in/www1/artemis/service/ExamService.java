package de.tum.in.www1.artemis.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
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
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Course.
 */
@Service
public class ExamService {

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private CourseService courseService;

    private final UserService userService;

    private final ExamRepository examRepository;

    private final ExerciseService exerciseService;

    private final StudentExamRepository studentExamRepository;

    private final ParticipationService participationService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final QuizExerciseService quizExerciseService;

    private final ExamQuizService examQuizService;

    private final InstanceMessageSendService instanceMessageSendService;

    public ExamService(ExamRepository examRepository, StudentExamRepository studentExamRepository, UserService userService, ParticipationService participationService,
            ProgrammingExerciseService programmingExerciseService, ExamQuizService examQuizService, ExerciseService exerciseService,
            InstanceMessageSendService instanceMessageSendService, QuizExerciseService quizExerciseService) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.userService = userService;
        this.participationService = participationService;
        this.programmingExerciseService = programmingExerciseService;
        this.examQuizService = examQuizService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.exerciseService = exerciseService;
        this.quizExerciseService = quizExerciseService;
    }

    @Autowired
    // break the dependency cycle
    public void setCourseService(CourseService courseService) {
        this.courseService = courseService;
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
     * @param examId {Long} The courseId of the course which contains the exam
     * @return The exam
     */
    public Exam findOneWithExercisesGroupsAndStudentExamsByExamId(Long examId) {
        log.debug("REST request to get the exam with student exams and exercise groups for Id : {}", examId);
        return examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
    }

    /**
     * Deletes all elements associated with the exam including:
     * <ul>
     *     <li>The Exam</li>
     *     <li>All ExerciseGroups</li>
     *     <li>All Exercises including:
     *     Submissions, Participations, Results, Repositories and Buildplans, see {@link ExerciseService#delete}</li>
     *     <li>All StudentExams</li>
     * </ul>
     * Note: StudentExams and ExerciseGroups are not explicitly deleted as the delete operation of the exam is cascaded by the database.
     * @param exam the exam to be deleted
     */
    public void delete(@NotNull Exam exam) {
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
     * Fetches the exam using {@link #findOneWithExercisesGroupsAndStudentExamsByExamId} which eagerly loads all required elements and calls {@link #delete}
     *
     * @param examId the ID of the exam to be deleted
     */
    public void deleteById(Long examId) {
        Exam exam = this.findOneWithExercisesGroupsAndStudentExamsByExamId(examId);
        this.delete(exam);
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

        // TODO: Check that this doesn't break the applications server
        List<StudentParticipation> studentParticipations = participationService.findByExamIdWithSubmissionRelevantResult(examId);

        // Adding exam information to DTO
        ExamScoresDTO scores = new ExamScoresDTO(exam.getId(), exam.getTitle(), exam.getMaxPoints());

        // Counts how many participants each exercise has
        Map<Long, Long> exerciseIdToNumberParticipations = studentParticipations.stream()
                .collect(Collectors.groupingBy(studentParticipation -> studentParticipation.getExercise().getId(), Collectors.counting()));

        // Adding exercise group information to DTO
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            // Find the maximum points for this exercise group
            OptionalDouble optionalMaxPointsGroup = exerciseGroup.getExercises().stream().mapToDouble(exercise -> exercise.getMaxScore()).max();
            Double maxPointsGroup = optionalMaxPointsGroup.orElse(0);

            // Counter for exerciseGroup participations. Is calculated by summing up the number of exercise participations
            long numberOfExeciseGroupParticipants = 0;
            // Add information about exercise groups and exercises
            var exerciseGroupDTO = new ExamScoresDTO.ExerciseGroup(exerciseGroup.getId(), exerciseGroup.getTitle(), maxPointsGroup);
            for (Exercise exercise : exerciseGroup.getExercises()) {
                Long participantsForExercise = exerciseIdToNumberParticipations.get(exercise.getId());
                // If no participation exists for an exercise then no entry exists in the map
                if (participantsForExercise == null) {
                    participantsForExercise = 0L;
                }
                numberOfExeciseGroupParticipants += participantsForExercise;
                exerciseGroupDTO.containedExercises
                        .add(new ExamScoresDTO.ExerciseGroup.ExerciseInfo(exercise.getId(), exercise.getTitle(), exercise.getMaxScore(), participantsForExercise));
            }
            exerciseGroupDTO.numberOfParticipants = numberOfExeciseGroupParticipants;
            scores.exerciseGroups.add(exerciseGroupDTO);
        }

        // Adding registered student information to DTO
        List<StudentExam> studentExams = studentExamRepository.findByExamId(examId);
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
                    double achievedPoints = relevantResult.getScore() / 100.0 * exercise.getMaxScore();
                    studentResult.overallPointsAchieved += Math.round(achievedPoints * 10) / 10.0;

                    // Check whether the student attempted to solve the exercise
                    boolean hasNonEmptySubmission = hasNonEmptySubmission(studentParticipation.getSubmissions(), exercise, objectMapper);
                    studentResult.exerciseGroupIdToExerciseResult.put(exercise.getExerciseGroup().getId(), new ExamScoresDTO.ExerciseResult(exercise.getId(), exercise.getTitle(),
                            exercise.getMaxScore(), relevantResult.getScore(), achievedPoints, hasNonEmptySubmission));
                }

            }

            if (scores.maxPoints != null) {
                studentResult.overallScoreAchieved = (studentResult.overallPointsAchieved / scores.maxPoints) * 100.0;
            }
            scores.studentResults.add(studentResult);
        }

        // Updating exam information in DTO
        Double sumOverallPoints = scores.studentResults.stream().mapToDouble(studentResult -> studentResult.overallPointsAchieved).sum();

        int numberOfStudentResults = scores.studentResults.size();

        if (numberOfStudentResults != 0) {
            scores.averagePointsAchieved = sumOverallPoints / numberOfStudentResults;
        }

        return scores;
    }

    /**
     * Checks whether one of the submissions is not empty
     *
     * @param submissions Submissions to check
     * @param exercise Exercise of the submissions
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
     * @param examId the id of the exam
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateStudentExams(Long examId) {
        // Delete all existing student exams via orphan removal (ignore test runs)
        Exam examWithExistingStudentExams = findWithStudentExamsById(examId);

        // TODO: the validation checks should happen in the resource, before this method is even being called!
        if (examWithExistingStudentExams.getNumberOfExercisesInExam() == null) {
            throw new BadRequestAlertException("The number of exercises must be set for the exam", "Exam", "artemisApp.exam.validation.numberOfExercisesMustBeSet");
        }

        // https://jira.spring.io/browse/DATAJPA-1367 deleteInBatch does not work, because it does not cascade the deletion of existing exam sessions, therefore use deleteAll
        studentExamRepository.deleteAll(examWithExistingStudentExams.getStudentExams());

        // now fetch the exam with additional information
        Exam exam = examRepository.findWithRegisteredUsersAndExerciseGroupsAndExercisesById(examId).get();

        List<ExerciseGroup> exerciseGroups = exam.getExerciseGroups();
        long numberOfOptionalExercises = exam.getNumberOfExercisesInExam() - exerciseGroups.stream().filter(ExerciseGroup::getIsMandatory).count();

        // Validate settings of the exam
        validateStudentExamGeneration(exam, numberOfOptionalExercises);

        // StudentExams are saved in the called method
        List<StudentExam> studentExams = createRandomStudentExams(exam, exam.getRegisteredUsers(), numberOfOptionalExercises);
        return studentExams;
    }

    /**
     * Generates the missing student exams randomly based on the exam configuration and the exercise groups.
     * The difference between all registered users and the users who already have an individual exam
     * is the set of users for which student exams will be created.
     *
     * @param examId        the id of the exam
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateMissingStudentExams(Long examId) {
        Exam exam = examRepository.findWithRegisteredUsersAndExerciseGroupsAndExercisesById(examId).get();

        // TODO: the validation checks should happen in the resource, before this method is even being called!
        if (exam.getNumberOfExercisesInExam() == null) {
            throw new BadRequestAlertException("The number of exercises must be set for the exam", "Exam", "artemisApp.exam.validation.numberOfExercisesMustBeSet");
        }

        long numberOfOptionalExercises = exam.getNumberOfExercisesInExam() - exam.getExerciseGroups().stream().filter(ExerciseGroup::getIsMandatory).count();

        // Validate settings of the exam
        validateStudentExamGeneration(exam, numberOfOptionalExercises);

        // Get all users who already have an individual exam
        Set<User> usersWithStudentExam = studentExamRepository.findUsersWithStudentExamsForExam(examId);

        // Get all registered users
        Set<User> allRegisteredUsers = exam.getRegisteredUsers();

        // Get all students who don't have an exam yet
        Set<User> missingUsers = new HashSet<>(allRegisteredUsers);
        missingUsers.removeAll(usersWithStudentExam);

        // StudentExams are saved in the called method
        List<StudentExam> missingStudentExams = createRandomStudentExams(exam, missingUsers, numberOfOptionalExercises);

        // TODO: make sure the student exams still contain non proxy users

        return missingStudentExams;
    }

    /**
     * Validates exercise settings.
     *
     * @param exam exam which is validated
     * @param numberOfOptionalExercises number of optional exercises in the exam
     * @throws BadRequestAlertException
     */
    private void validateStudentExamGeneration(Exam exam, long numberOfOptionalExercises) throws BadRequestAlertException {
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
    }

    /**
     * Generates random exams for each user in the given users set and saves them.
     *
     * @param exam exam for which the individual student exams will be generated
     * @param users users for which the individual exams will be generated
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
     *
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @param studentDtos   the list of students (with at least registration number) who should get access to the exam
     * @return the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    public List<StudentDTO> registerStudentsForExam(Long courseId, Long examId, List<StudentDTO> studentDtos) {
        var course = courseService.findOne(courseId);
        var exam = findOneWithRegisteredUsers(examId);
        List<StudentDTO> notFoundStudentsDtos = new ArrayList<>();
        for (var studentDto : studentDtos) {
            var registrationNumber = studentDto.getRegistrationNumber();
            try {
                // 1) we use the registration number and try to find the student in the Artemis user database
                Optional<User> optionalStudent = userService.findUserWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
                if (optionalStudent.isPresent()) {
                    var student = optionalStudent.get();
                    // we only need to add the student to the course group, if the student is not yet part of it, otherwise the student cannot access the exam (within the course)
                    if (!student.getGroups().contains(course.getStudentGroupName())) {
                        userService.addUserToGroup(student, course.getStudentGroupName());
                    }
                    exam.addRegisteredUser(student);
                    continue;
                }
                // 2) if we cannot find the student, we use the registration number and try to find the student in the (TUM) LDAP, create it in the Artemis DB and in a potential
                // external user management system
                optionalStudent = userService.createUserFromLdap(registrationNumber);
                if (optionalStudent.isPresent()) {
                    var student = optionalStudent.get();
                    // the newly created student needs to get the rights to access the course, otherwise the student cannot access the exam (within the course)
                    userService.addUserToGroup(student, course.getStudentGroupName());
                    exam.addRegisteredUser(student);
                    continue;
                }
                // 3) if we cannot find the user in the (TUM) LDAP, we report this to the client
                log.warn("User with registration number " + registrationNumber + " not found in Artemis user database and not found in (TUM) LDAP");
            }
            catch (Exception ex) {
                log.warn("Error while processing user with registration number " + registrationNumber + ": " + ex.getMessage(), ex);
            }

            notFoundStudentsDtos.add(studentDto);
        }
        examRepository.save(exam);
        return notFoundStudentsDtos;
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
     * Finds an exam based on the id with all student exams which are not marked as test runs.
     * @param examId the id of the exam
     * @return the exam with student exams loaded
     */
    private Exam findWithStudentExamsById(long examId) {
        Exam exam = examRepository.findWithStudentExamsById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id " + examId + " does not exist"));
        // drop all test runs and set the remaining student exams to the exam
        exam.setStudentExams(exam.getStudentExams().stream().dropWhile(StudentExam::getTestRun).collect(Collectors.toSet()));
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
    public Integer startExercises(Long examId) {

        var exam = examRepository.findWithStudentExamsExercisesParticipationsSubmissionsById(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

        var studentExams = exam.getStudentExams();

        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());

        executeInParallel(() -> studentExams.parallelStream().forEach(studentExam -> setUpExerciseParticipationsAndSubmissions(generatedParticipations, studentExam)));

        return generatedParticipations.size();
    }

    /**
     * Sets up the participations and submissions for all the exercises of the student exam.
     * @param generatedParticipations List of generatedParticipations
     * @param studentExam The studentExam
     */
    public void setUpExerciseParticipationsAndSubmissions(List<StudentParticipation> generatedParticipations, StudentExam studentExam) {
        User student = studentExam.getUser();
        for (Exercise exercise : studentExam.getExercises()) {
            // we start the exercise if no participation was found that was already fully initialized
            if (exercise.getStudentParticipations().stream().noneMatch(studentParticipation -> studentParticipation.getParticipant().equals(student)
                    && studentParticipation.getInitializationState() != null && studentParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
                try {
                    SecurityUtils.setAuthorizationObject();
                    if (exercise instanceof ProgrammingExercise) {
                        // Load lazy property
                        final var programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exercise.getId());
                        ((ProgrammingExercise) exercise).setTemplateParticipation(programmingExercise.getTemplateParticipation());
                    }
                    // this will create initial (empty) submissions for quiz, text, modeling and file upload
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
        log.info("Evaluating {} quiz exercies in exam {}", quizExercises.size(), examId);
        // Evaluate all quizzes for that exercise
        quizExercises.forEach(quiz -> examQuizService.evaluateQuizAndUpdateStatistics(quiz.getId()));
        log.info("Evaluated {} quiz exercies in exam {} in {}", quizExercises.size(), examId, TimeLogUtil.formatDurationFrom(start));

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
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param examId the id of the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public ZonedDateTime getLatestIndiviudalExamEndDate(Long examId) {
        return getLatestIndiviudalExamEndDate(findOne(examId));
    }

    /**
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param exam the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     */
    public ZonedDateTime getLatestIndiviudalExamEndDate(Exam exam) {
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
    public Set<ZonedDateTime> getAllIndiviudalExamEndDates(Long examId) {
        return getAllIndiviudalExamEndDates(findOne(examId));
    }

    /**
     * Returns all individual exam end dates as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, an empty set returned.
     *
     * @param exam the exam
     * @return a set of all end dates. May return an empty set, if the exam has no start/end date or student exams cannot be found.
     */
    public Set<ZonedDateTime> getAllIndiviudalExamEndDates(Exam exam) {
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

}
