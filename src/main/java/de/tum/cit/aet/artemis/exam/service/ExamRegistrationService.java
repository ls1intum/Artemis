package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.util.TimeLogUtil.formatDurationFrom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.ExamRegistrationResultDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUserDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;

/**
 * Service Implementation for registering students in the exam.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ExamRegistrationService.class);

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final UserService userService;

    private final ParticipationDeletionService participationDeletionService;

    private final StudentExamRepository studentExamRepository;

    private final AuditEventRepository auditEventRepository;

    private final ExamRepository examRepository;

    private final CourseRepository courseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserCourseRoleRepository userCourseRoleRepository;

    private final ExamUserService examUserService;

    private static final boolean IS_TEST_RUN = false;

    private final StudentExamService studentExamService;

    public ExamRegistrationService(ExamUserRepository examUserRepository, ExamRepository examRepository, UserService userService,
            ParticipationDeletionService participationDeletionService, UserRepository userRepository, AuditEventRepository auditEventRepository, CourseRepository courseRepository,
            StudentExamRepository studentExamRepository, StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authorizationCheckService,
            UserCourseRoleRepository userCourseRoleRepository, ExamUserService examUserService, StudentExamService studentExamService) {
        this.examRepository = examRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.participationDeletionService = participationDeletionService;
        this.auditEventRepository = auditEventRepository;
        this.courseRepository = courseRepository;
        this.studentExamRepository = studentExamRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.examUserRepository = examUserRepository;
        this.examUserService = examUserService;
        this.studentExamService = studentExamService;
    }

    /**
     * Add multiple users to the students of the exam so that they can access the exam.
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login).
     * <p>
     * This method first tries to find the user in the internal Artemis user database (because the user is probably already using Artemis).
     * In case the user cannot be found, it additionally searches the connected LDAP in case it is configured.
     * <p>
     * Users who hold a staff role (instructor, editor, tutor, or admin) in the course are rejected and reported back
     * in {@link ExamRegistrationResultDTO#rejectedStaffUsers()}. Such users are NOT added to the course student group,
     * so a failed registration leaves no side effect on the user's course membership.
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param examUserDTOs the list of students (with at least one unique identifier) who should get access to the exam
     * @return a result containing the students who could not be found and the staff members who were rejected
     */
    public ExamRegistrationResultDTO registerStudentsForExam(Long courseId, Long examId, List<ExamUserDTO> examUserDTOs) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);

        if (exam.isTestExam()) {
            throw new AccessForbiddenException("Registration of students is only allowed for real exams");
        }

        // Pre-fetch the ids of all course staff once to avoid one isStaffMemberOfCourse EXISTS query per submitted student.
        Set<Long> staffUserIds = userCourseRoleRepository.findUsersByCourse_IdAndRoleIn(course.getId(), CourseRole.valuesAtLeast(CourseRole.TEACHING_ASSISTANT)).stream()
                .map(User::getId).collect(Collectors.toSet());

        List<ExamUserDTO> notFoundStudentsDTOs = new ArrayList<>();
        List<ExamUserDTO> rejectedStaffDTOs = new ArrayList<>();
        List<String> usersAddedToExamForLogging = new ArrayList<>();

        record ResolvedStudent(ExamUserDTO dto, User student) {
        }
        List<ResolvedStudent> resolvedStudents = new ArrayList<>();
        for (var examUserDto : examUserDTOs) {
            // Resolve the user WITHOUT enrolling them in the course yet, so that rejected staff leave no side effect
            Optional<User> optionalStudent = userService.findUser(examUserDto.registrationNumber(), examUserDto.login(), examUserDto.email());
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(examUserDto);
                continue;
            }

            User student = optionalStudent.get();

            // Reject staff (instructor, editor, tutor, admin) BEFORE granting any course access
            if (staffUserIds.contains(student.getId()) || authorizationCheckService.isAdmin(student)) {
                rejectedStaffDTOs.add(examUserDto);
                continue;
            }

            resolvedStudents.add(new ResolvedStudent(examUserDto, student));
        }

        // Only users who will actually be registered get enrolled. Batching them keeps this to a single round trip
        // instead of one existsBy query + insert per student.
        userService.addUsersToCourse(resolvedStudents.stream().map(ResolvedStudent::student).toList(), course, CourseRole.STUDENT);

        // exam.getExamUsers() is already eagerly loaded, so this avoids one findByExamIdAndUserId query per student.
        Map<Long, ExamUser> existingExamUsersByUserId = exam.getExamUsers().stream().collect(Collectors.toMap(eu -> eu.getUser().getId(), eu -> eu));

        List<ExamUser> examUsersToCreate = new ArrayList<>();
        List<ExamUser> examUsersToUpdate = new ArrayList<>();

        for (var resolved : resolvedStudents) {
            var examUserDto = resolved.dto();
            User student = resolved.student();
            ExamUser existing = existingExamUsersByUserId.get(student.getId());

            if (existing == null) {
                ExamUser registeredExamUser = new ExamUser();
                registeredExamUser.setUser(student);
                registeredExamUser.setExam(exam);

                if (StringUtils.hasText(examUserDto.room())) {
                    registeredExamUser.setPlannedRoom(examUserDto.room());
                }
                if (StringUtils.hasText(examUserDto.seat())) {
                    registeredExamUser.setPlannedSeat(examUserDto.seat());
                }
                examUsersToCreate.add(registeredExamUser);
                usersAddedToExamForLogging.add(student.getLogin());
            }
            else {
                // Update room/seat of an already registered exam user
                if (StringUtils.hasText(examUserDto.room())) {
                    existing.setPlannedRoom(examUserDto.room());
                }
                if (StringUtils.hasText(examUserDto.seat())) {
                    existing.setPlannedSeat(examUserDto.seat());
                }
                examUsersToUpdate.add(existing);
                usersAddedToExamForLogging.add(existing.getUser().getLogin());
            }
        }

        // Batch-insert/update all exam users in two round trips instead of one INSERT/UPDATE per student.
        examUserRepository.saveAll(examUsersToCreate).forEach(exam::addExamUser);
        examUserRepository.saveAll(examUsersToUpdate);

        examRepository.save(exam);
        studentExamService.invalidateExerciseStartStatus(exam.getId());

        if (exam.isStarted()) {
            // Generate student exams for the registered students if the exam has already started and prepare the exercises
            List<StudentExam> newStudentExams = studentExamService.generateMissingStudentExams(exam);
            List<Long> studentExamIds = newStudentExams.stream().map(DomainObject::getId).toList();
            long start = System.nanoTime();
            studentExamService.startExercisesForStudentExams(exam.getId(), studentExamIds).thenAccept(numberOfGeneratedParticipations -> log
                    .info("Generated {} participations in {} for student exams of exam {}", numberOfGeneratedParticipations, formatDurationFrom(start), examId));
        }

        try {
            User currentUser = userRepository.getUserWithAuthorities();
            Map<String, Object> userData = new HashMap<>();
            userData.put("exam", exam.getTitle());
            for (var i = 0; i < examUserDTOs.size(); i++) {
                var studentDTO = examUserDTOs.get(i);
                userData.put("student" + i, studentDTO.login() + " (" + studentDTO.registrationNumber() + ")");
            }
            AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, userData);
            auditEventRepository.add(auditEvent);
            log.info("User {} has added multiple users {} to the exam {} with id {}", currentUser.getLogin(), usersAddedToExamForLogging, exam.getTitle(), exam.getId());
        }
        catch (Exception ex) {
            log.warn("Could not add audit event to audit log", ex);
        }

        return new ExamRegistrationResultDTO(notFoundStudentsDTOs, rejectedStaffDTOs);
    }

    /**
     * Returns <code>true</code> if the current user is registered for the exam
     *
     * @param examId the id of the exam
     * @return <code>true</code> if the user is registered for the exam, false if this is not the case or the exam does not exist
     */
    public boolean isCurrentUserRegisteredForExam(Long examId) {
        return isUserRegisteredForExam(examId, userRepository.getUser().getId());
    }

    /**
     * Returns <code>true</code> if the user with the given id is registered for the exam
     *
     * @param examId the id of the exam
     * @param userId the id of the user to check
     * @return <code>true</code> if the user is registered for the exam, false if this is not the case or the exam does not exist
     */
    public boolean isUserRegisteredForExam(Long examId, Long userId) {
        return examRepository.isUserRegisteredForExam(examId, userId);
    }

    /**
     * Checks if the current User is registered for the test exam, otherwise the User is registered to the test exam.
     * The calling user must be registered in the respective course
     *
     * @param course      the course containing the exam
     * @param examId      the examId for which we want to register a student
     * @param currentUser the user to be registered in the exam
     */
    public void checkRegistrationOrRegisterStudentToTestExam(Course course, long examId, User currentUser) {
        Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);

        if (!exam.isTestExam()) {
            throw new BadRequestAlertException("Self-Registration is only allowed for test exams", "ExamRegistrationService", "SelfRegistrationOnlyForRealExams");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);
        Optional<ExamUser> registeredExamUserOptional = examUserRepository.findByExamIdAndUserId(exam.getId(), currentUser.getId());
        ExamUser registeredExamUser = null;
        if (registeredExamUserOptional.isEmpty()) {
            registeredExamUser = createExamUser(exam, currentUser);
        }

        // We only need to update the registered exam users, if the user is not yet registered for the test exam
        if (registeredExamUser != null && !exam.getExamUsers().contains(registeredExamUser)) {
            exam.addExamUser(registeredExamUser);
            examRepository.save(exam);

            AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, "TestExam=" + exam.getTitle());
            auditEventRepository.add(auditEvent);
            log.info("User {} has self-registered to the test exam {} with id {}", currentUser.getLogin(), exam.getTitle(), exam.getId());
        }
    }

    /**
     * @param exam                              the exam with eagerly loaded registered users for which a student should be unregistered
     * @param deleteParticipationsAndSubmission whether the participations and submissions of the student should be deleted
     * @param student                           the user object that should be unregistered
     */
    public void unregisterStudentFromExam(Exam exam, boolean deleteParticipationsAndSubmission, User student) {
        ExamUser registeredExamUser = examUserRepository.findByExamIdAndUserId(exam.getId(), student.getId())
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + student.getLogin() + "\" is not registered to the exam with id: \"" + exam.getId() + "\""));
        exam.removeExamUser(registeredExamUser);

        // Note: we intentionally do not remove the user from the course, because the student might just have "unregistered" from the exam, but should
        // still have access to the course.
        examRepository.save(exam);
        examUserRepository.delete(registeredExamUser);

        examUserService.deleteAvailableExamUserImages(registeredExamUser);

        // The student exam might already be generated, then we need to delete it
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(student.getId(), exam.getId(), IS_TEST_RUN);
        optionalStudentExam.ifPresent(studentExam -> removeStudentExam(studentExam, deleteParticipationsAndSubmission));
        studentExamService.invalidateExerciseStartStatus(exam.getId());

        User currentUser = userRepository.getUserWithAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.REMOVE_USER_FROM_EXAM, "exam=" + exam.getTitle(), "user=" + student.getLogin());
        auditEventRepository.add(auditEvent);
        log.info("User {} has removed user {} from the exam {} with id {}. This also deleted a potentially existing student exam with all its participations and submissions.",
                currentUser.getLogin(), student.getLogin(), exam.getTitle(), exam.getId());
    }

    private void removeStudentExam(StudentExam studentExam, boolean deleteParticipationsAndSubmission) {

        // Optionally delete participations and submissions
        if (deleteParticipationsAndSubmission) {
            List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerSubmissions(studentExam);
            for (var participation : participations) {
                participationDeletionService.delete(participation.getId(), true);
            }
        }

        // Delete the student exam
        studentExamRepository.deleteById(studentExam.getId());
    }

    /**
     * Unregisters all students from the exam
     *
     * @param exam                              the exam with eagerly loaded registered users for which all students should be unregistered
     * @param deleteParticipationsAndSubmission whether the participations and submissions of the student should be deleted
     */
    public void unregisterAllStudentFromExam(Exam exam, boolean deleteParticipationsAndSubmission) {
        // remove all registered students
        List<ExamUser> registeredExamUsers = examUserRepository.findAllByExamId(exam.getId());
        registeredExamUsers.forEach(exam::removeExamUser);
        examRepository.save(exam);
        examUserRepository.deleteAllById(registeredExamUsers.stream().map(ExamUser::getId).toList());

        registeredExamUsers.forEach(examUserService::deleteAvailableExamUserImages);

        // remove all students exams
        Set<StudentExam> studentExams = studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(exam.getId());
        studentExams.forEach(studentExam -> removeStudentExam(studentExam, deleteParticipationsAndSubmission));
        studentExamService.invalidateExerciseStartStatus(exam.getId());

        User currentUser = userRepository.getUserWithAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.REMOVE_ALL_USERS_FROM_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has removed all users from the exam {} with id {}. This also deleted potentially existing student exams with all its participations and submissions.",
                currentUser.getLogin(), exam.getTitle(), exam.getId());
    }

    /**
     * Adds all students registered in the course to the given exam
     *
     * @param courseId Id of the course
     * @param exam     the exam with eagerly loaded registered users to which the course students should be added
     */
    public void addAllStudentsOfCourseToExam(Long courseId, Exam exam) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        // Load students with their authorities eagerly so that isAdmin() can access
        // user.getAuthorities() without triggering a LazyInitializationException on
        // the detached entity after the Hibernate session has been closed.
        var students = new ArrayList<>(userRepository.findAllByCourseIdAndCourseRolesInWithAuthorities(course.getId(), Set.of(CourseRole.STUDENT)));

        // Pre-fetch already-registered user IDs from the eagerly loaded exam users to avoid one per-student DB query.
        Set<Long> registeredUserIds = exam.getExamUsers() != null ? exam.getExamUsers().stream().map(eu -> eu.getUser().getId()).collect(Collectors.toSet()) : Set.of();
        // Pre-fetch the ids of all course staff once to avoid one isStaffMemberOfCourse EXISTS query per student.
        Set<Long> staffUserIds = userCourseRoleRepository.findUsersByCourse_IdAndRoleIn(course.getId(), CourseRole.valuesAtLeast(CourseRole.TEACHING_ASSISTANT)).stream()
                .map(User::getId).collect(Collectors.toSet());

        Map<String, Object> userData = new HashMap<>();
        userData.put("exam", exam.getTitle());
        List<ExamUser> newExamUsers = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            var student = students.get(i);
            if (!registeredUserIds.contains(student.getId()) && !staffUserIds.contains(student.getId()) && !authorizationCheckService.isAdmin(student)) {
                ExamUser examUser = new ExamUser();
                examUser.setExam(exam);
                examUser.setUser(student);
                newExamUsers.add(examUser);
                userData.put("student " + i, student.toDatabaseString());
            }
        }

        // Batch-insert all new exam users in one round trip instead of one INSERT per student.
        examUserRepository.saveAll(newExamUsers).forEach(exam::addExamUser);

        examRepository.save(exam);
        studentExamService.invalidateExerciseStartStatus(exam.getId());
        AuditEvent auditEvent = new AuditEvent(userRepository.getUser().getLogin(), Constants.ADD_USER_TO_EXAM, userData);
        auditEventRepository.add(auditEvent);
    }

    private ExamUser createExamUser(Exam exam, User user) {
        ExamUser examUser = new ExamUser();
        examUser.setExam(exam);
        examUser.setUser(user);
        return examUserRepository.save(examUser);
    }

    /**
     * Checks whether the given user holds a staff role (instructor, editor, tutor, or admin) in the course
     * and therefore must not be registered as an exam student.
     *
     * @param course the course the exam belongs to
     * @param user   the user to check
     * @return true if the user is course staff and may not be registered as an exam student
     */
    public boolean isStaffMemberOfCourse(Course course, User user) {
        return authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }
}
