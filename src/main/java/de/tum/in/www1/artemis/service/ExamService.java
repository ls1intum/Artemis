package de.tum.in.www1.artemis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
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

    private final StudentExamRepository studentExamRepository;

    private final ParticipationService participationService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ExamQuizService examQuizService;

    public ExamService(ExamRepository examRepository, StudentExamRepository studentExamRepository, UserService userService, ParticipationService participationService,
            ProgrammingExerciseService programmingExerciseService, ExamQuizService examQuizService) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.userService = userService;
        this.participationService = participationService;
        this.programmingExerciseService = programmingExerciseService;
        this.examQuizService = examQuizService;
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
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NotNull
    public Exam findOneWithExerciseGroupsAndExercises(Long examId) {
        log.debug("Request to get exam with exercise groups : {}", examId);
        return examRepository.findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
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
     * Get the exam of a course with exercise groups and student exams
     * @param examId {Long} The courseId of the course which contains the exam
     * @return The exam
     */
    public Exam findOneWithExercisesGroupsAndStudentExamsByExamId(Long examId) {
        log.debug("REST request to get the exam with student exams and exercise groups for Id : {}", examId);
        return examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
    }

    /**
     * Delete the exam by id.
     *
     * @param examId the id of the entity
     */
    public void delete(Long examId) {
        log.debug("Request to delete exam : {}", examId);
        examRepository.deleteById(examId);
    }

    /**
     * Filters the visible exams (excluding the ones that are not visible yet)
     *
     * @param exams a set of exams (e.g. the ones of a course)
     * @return only the visible exams
     */
    public Set<Exam> filterVisibleExams(Set<Exam> exams) {
        return exams.stream().filter(Exam::isVisibleToStudents).collect(Collectors.toSet());
    }

    /**
     * Returns the relevant result of a student participation
     *
     * @param studentParticipation studentParticipation to get relevant result for
     * @return relevant result of student participation or null if none exists
     */
    private Result getRelevantResult(StudentParticipation studentParticipation) {
        // no participant -> no relevant result
        if (studentParticipation.getParticipant() == null) {
            return null;
        }

        Optional<Result> relevantResult = studentParticipation.getResults().stream().filter(Result::isRated).filter(result -> result.getCompletionDate() != null)
                .filter(result -> result.getScore() != null).sorted((r1, r2) -> r2.getCompletionDate().compareTo(r1.getCompletionDate())).findFirst();

        return relevantResult.orElse(null);
    }

    /**
     * Rounds a double to a specified number of decimal places
     *
     * @param value  the number to round
     * @param places the number of decimal places to round to
     * @return rounded number
     */
    public static double round(double value, int places) {
        if (places < 0)
            throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    /**
     * Puts students, result and exerciseGroups together for ExamScoresDTO
     *
     * @param examId the id of the exam
     * @return return ExamScoresDTO with students, scores and exerciseGroups for exam
     */
    public ExamScoresDTO getExamScore(Long examId) {
        Exam exam = examRepository.findForScoreCalculationById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

        // Adding exam information to DTO
        ExamScoresDTO scores = new ExamScoresDTO(exam.getId(), exam.getTitle(), exam.getMaxPoints());

        // Adding exercise group information to DTO
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            // Alert: This only works if all exercises in an exercise groups have the same number of maximum points
            Double maximumNumberOfPoints = null;
            if (!exerciseGroup.getExercises().isEmpty()) {
                maximumNumberOfPoints = exerciseGroup.getExercises().iterator().next().getMaxScore();
            }

            List<String> containedExercises = new ArrayList<>();

            for (Exercise exercise : exerciseGroup.getExercises()) {
                containedExercises.add(exercise.getTitle().trim());
            }

            scores.exerciseGroups.add(new ExamScoresDTO.ExerciseGroup(exerciseGroup.getId(), exerciseGroup.getTitle(), maximumNumberOfPoints, containedExercises));
        }

        // Adding registered student information to DTO
        for (User user : exam.getRegisteredUsers()) {
            scores.studentResults.add(new ExamScoresDTO.StudentResult(user.getId(), user.getName(), user.getEmail(), user.getLogin(),
                    user.getRegistrationNumber() != null ? user.getRegistrationNumber().trim() : ""));
        }

        List<StudentParticipation> studentParticipations = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream)
                .map(Exercise::getStudentParticipations).flatMap(Collection::stream).collect(Collectors.toList());

        // Adding student results information to DTO
        for (ExamScoresDTO.StudentResult studentResult : scores.studentResults) {
            // ToDo Support Team Exercises
            List<StudentParticipation> participationsOfStudent = studentParticipations.stream()
                    .filter(studentParticipation -> studentParticipation.getStudent().get().getId() == studentResult.id).collect(Collectors.toList());

            for (StudentParticipation studentParticipation : participationsOfStudent) {
                Exercise exercise = studentParticipation.getExercise();

                Result relevantResult = getRelevantResult(studentParticipation);

                if (relevantResult != null) {
                    Result result = studentParticipation.getResults().iterator().next();
                    Double achievedPoints = round((result.getScore() / 100.0 * exercise.getMaxScore()), 2);

                    if (studentResult.overallPointsAchieved == null) {
                        studentResult.overallPointsAchieved = achievedPoints;
                    }
                    else {
                        studentResult.overallPointsAchieved = studentResult.overallPointsAchieved + achievedPoints;
                    }

                    studentResult.exerciseGroupIdToExerciseResult.put(exercise.getExerciseGroup().getId(),
                            new ExamScoresDTO.ExerciseResult(exercise.getId(), exercise.getTitle(), exercise.getMaxScore(), result.getScore(), achievedPoints));
                }
            }
        }

        // Updating student result information in DTO
        for (ExamScoresDTO.StudentResult studentResult : scores.studentResults) {
            if (studentResult.overallPointsAchieved != null) {
                studentResult.overallScoreAchieved = round((studentResult.overallPointsAchieved / scores.maxPoints) * 100.0, 2);
            }
        }

        // Updating exerciseGroup information in DTO
        for (ExamScoresDTO.ExerciseGroup exerciseGroup : scores.exerciseGroups) {
            Double noOfFoundResults = 0.0;
            Double sumOfPoints = 0.0;

            for (ExamScoresDTO.StudentResult studentResult : scores.studentResults) {
                if (studentResult.exerciseGroupIdToExerciseResult.containsKey(exerciseGroup.id)) {
                    ExamScoresDTO.ExerciseResult exerciseResult = studentResult.exerciseGroupIdToExerciseResult.get(exerciseGroup.id);
                    noOfFoundResults++;
                    sumOfPoints = sumOfPoints + exerciseResult.achievedPoints;
                }
            }

            if (noOfFoundResults != 0.0) {
                exerciseGroup.averagePointsAchieved = round((sumOfPoints / noOfFoundResults), 2);
            }
        }

        // Uptading exam information in DTO
        Double sumOverallPoints = scores.studentResults.stream().filter(studentResult -> studentResult.overallPointsAchieved != null)
                .map(studentResult -> studentResult.overallPointsAchieved).reduce(0.0, Double::sum);

        Long numberOfStudentResultsWithOverallPoints = scores.studentResults.stream().filter(studentResult -> studentResult.overallPointsAchieved != null).count();

        if (numberOfStudentResultsWithOverallPoints != 0.0) {
            scores.averagePointsAchieved = round((sumOverallPoints / numberOfStudentResultsWithOverallPoints), 2);
        }

        return scores;
    }

    /**
     * Generates the student exams randomly based on the exam configuration and the exercise groups
     *
     * @param examId the id of the exam
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateStudentExams(Long examId) {
        List<StudentExam> studentExams = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        // Delete all existing student exams via orphan removal
        Exam examWithExistingStudentExams = examRepository.findWithStudentExamsById(examId).get();
        studentExamRepository.deleteInBatch(examWithExistingStudentExams.getStudentExams());

        Exam exam = examRepository.findWithExercisesRegisteredUsersStudentExamsById(examId).get();

        // Check that the start and end date of the exam is set
        if (exam.getStartDate() == null || exam.getEndDate() == null) {
            throw new BadRequestAlertException("The start and end date must be set for the exam", "Exam", "artemisApp.exam.validation.startAndEndMustBeSet");
        }

        // Determine the default working time by computing the duration between start and end date of the exam
        Integer defaultWorkingTime = Math.toIntExact(Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds());

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

        List<ExerciseGroup> exerciseGroups = exam.getExerciseGroups();

        // Check that there are enough exercise groups
        if (exerciseGroups.size() < exam.getNumberOfExercisesInExam()) {
            throw new BadRequestAlertException("The number of exercise groups is too small", "Exam", "artemisApp.exam.validation.tooFewExerciseGroups");
        }

        long numberOfOptionalExercises = exam.getNumberOfExercisesInExam() - exerciseGroups.stream().filter(ExerciseGroup::getIsMandatory).count();

        // Check that there are not too much mandatory exercise groups
        if (numberOfOptionalExercises < 0) {
            throw new BadRequestAlertException("The number of mandatory exercise groups is too large", "Exam", "artemisApp.exam.validation.tooManyMandatoryExerciseGroups");
        }

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

        for (User registeredUser : exam.getRegisteredUsers()) {
            // Create one student exam per user
            StudentExam studentExam = new StudentExam();
            studentExam.setWorkingTime(defaultWorkingTime);
            studentExam.setExam(exam);
            studentExam.setUser(registeredUser);

            // Add a random exercise for each exercise group if the index of the exercise group is in assembledIndices
            List<Integer> assembledIndices = assembleIndicesListWithRandomSelection(indicesOfMandatoryExerciseGroups, indicesOfOptionalExerciseGroups, numberOfOptionalExercises);
            for (Integer index : assembledIndices) {
                // We get one random exercise from all preselected exercise groups
                studentExam.addExercise(selectRandomExercise(random, exerciseGroups.get(index)));
            }

            // Apply random exercise order
            if (Boolean.TRUE.equals(exam.getRandomizeExerciseOrder())) {
                Collections.shuffle(studentExam.getExercises());
            }

            studentExams.add(studentExam);
        }

        studentExams = studentExamRepository.saveAll(studentExams);

        // TODO: make sure the student exams still contain non proxy users

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
    public List<StudentDTO> registerStudentsForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<StudentDTO> studentDtos) {
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
                    exam.addUser(student);
                    continue;
                }
                // 2) if we cannot find the student, we use the registration number and try to find the student in the (TUM) LDAP, create it in the Artemis DB and in a potential
                // external user management system
                optionalStudent = userService.createUserFromLdap(registrationNumber);
                if (optionalStudent.isPresent()) {
                    var student = optionalStudent.get();
                    // the newly created student needs to get the rights to access the course, otherwise the student cannot access the exam (within the course)
                    userService.addUserToGroup(student, course.getStudentGroupName());
                    exam.addUser(student);
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

        List<Participation> generatedParticipations = new ArrayList<>();

        for (StudentExam studentExam : studentExams) {
            User student = studentExam.getUser();
            for (Exercise exercise : studentExam.getExercises()) {
                // we start the exercise if no participation was found that was already fully initialized
                if (exercise.getStudentParticipations().stream()
                        .noneMatch(studentParticipation -> studentParticipation.getParticipant().equals(student) && studentParticipation.getInitializationState() != null
                                && studentParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
                    try {
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
                        log.warn("Start exercise for student exam {} and exercise {} and student {}", studentExam.getId(), exercise.getId(), student.getId());
                    }
                }
            }
        }

        return generatedParticipations.size();
    }

    /**
     * Evaluates all the quiz exercises of an exam
     *
     * @param examId id of the exam for which the quiz exercises should be evaluated
     * @return number of evaluated exercises
     */
    public Integer evaluateQuizExercises(Long examId) {
        var exam = examRepository.findWithExercisesRegisteredUsersStudentExamsById(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));

        // Collect all quiz exercises for the given exam
        Set<QuizExercise> quizExercises = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof QuizExercise) {
                    quizExercises.add((QuizExercise) exercise);
                }
            }
        }

        // Evaluate all quizzes for that exercise
        quizExercises.forEach(examQuizService::evaluateQuizAndUpdateStatistics);

        return quizExercises.size();
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
        if (exam.getStartDate() == null)
            return null;
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
        if (exam.getStartDate() == null)
            return null;
        var workingTimes = studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId());
        return workingTimes.stream().map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).collect(Collectors.toSet());
    }
}
