package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstancesBatchCreateDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStatisticsDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStudentRowDTO;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.StringUtil;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseIdAndTitleDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Service for managing course-level presentation assessments.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PresentationAssessmentService {

    private static final LocalDate EARLIEST_PRESENTATION_DATE = LocalDate.of(1970, 1, 1);

    private static final Set<CourseRole> ASSESSOR_ROLES = Set.copyOf(CourseRole.valuesAtLeast(CourseRole.TEACHING_ASSISTANT));

    private static final int ASSESSOR_SEARCH_MIN_LENGTH = 3;

    private static final int ASSESSOR_SEARCH_RESULT_LIMIT = 25;

    private final PresentationAssessmentRepository presentationAssessmentRepository;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    private final CourseRepository courseRepository;

    private final CourseConfigurationRepository courseConfigurationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public PresentationAssessmentService(PresentationAssessmentRepository presentationAssessmentRepository, UserRepository userRepository, ExerciseRepository exerciseRepository,
            PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository, CourseRepository courseRepository,
            CourseConfigurationRepository courseConfigurationRepository, AuthorizationCheckService authorizationCheckService) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.presentationAssessmentInstanceRepository = presentationAssessmentInstanceRepository;
        this.courseRepository = courseRepository;
        this.courseConfigurationRepository = courseConfigurationRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Verifies that presentation assessments are enabled for the course.
     *
     * @param courseId the course id
     */
    public void checkPresentationAssessmentsEnabled(long courseId) {
        if (courseConfigurationRepository.findPresentationAssessmentsEnabledByCourseId(courseId).filter(Boolean::booleanValue).isEmpty()) {
            throw new AccessForbiddenException("Presentation assessments are disabled for this course.");
        }
    }

    /**
     * Loads course-wide presentation assessment counts.
     *
     * @param courseId the course id
     * @return the total and assessed instance counts
     */
    public PresentationAssessmentStatisticsDTO getStatistics(long courseId) {
        checkPresentationAssessmentsEnabled(courseId);
        return presentationAssessmentInstanceRepository.findStatisticsByCourseId(courseId);
    }

    /**
     * Searches the course staff that can be chosen as the assessor of a presentation.
     *
     * @param courseId   the course id
     * @param searchTerm text to search in the login or full name, at least three characters long
     * @return the name and login of the matching tutors, editors and instructors ordered by name, at most 25
     */
    public List<UserNameAndLoginDTO> searchAssessorOptions(long courseId, String searchTerm) {
        if (searchTerm.trim().length() < ASSESSOR_SEARCH_MIN_LENGTH) {
            throw new BadRequestAlertException("The search term must be at least three characters long", PresentationAssessmentInstance.ENTITY_NAME, "searchTermTooShort");
        }
        checkPresentationAssessmentsEnabled(courseId);
        String searchPattern = "%" + StringUtil.escapeForLikeLowerCase(searchTerm) + "%";
        return userRepository.searchNameAndLoginByLoginOrNameInCourseWithRoles(courseId, ASSESSOR_ROLES, searchPattern, PageRequest.of(0, ASSESSOR_SEARCH_RESULT_LIMIT));
    }

    private PresentationAssessment findByIdAndCourseIdElseThrow(long courseId, long assessmentId) {
        return presentationAssessmentRepository.findOneByIdAndCourseId(assessmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, assessmentId));
    }

    /**
     * Loads a filtered page of individual presentation assessment rows.
     *
     * @param courseId         the owning course id
     * @param assessmentId     the presentation assessment id, or null for no filter
     * @param assessed         whether result points are assigned, or null for no filter
     * @param linkedToExercise whether the presentation is linked to an exercise, or null for no filter
     * @param searchTerm       text to search in student login, email, full name, or presentation title; null or blank disables searching
     * @param page             zero-based page index
     * @param size             page size between 1 and 100
     * @param sortField        studentLogin, presentationTitle, presentationDate, or resultPoints
     * @param direction        sorting direction
     * @return the matching rows in the requested order, including the total number of matches
     */
    public Page<PresentationAssessmentStudentRowDTO> getStudentRows(long courseId, Long assessmentId, Boolean assessed, Boolean linkedToExercise, String searchTerm, int page,
            int size, String sortField, Sort.Direction direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestAlertException("Invalid pagination parameters", PresentationAssessmentInstance.ENTITY_NAME, "invalidPagination");
        }

        checkPresentationAssessmentsEnabled(courseId);
        Pageable pageable = createStudentRowsPageable(page, size, sortField, direction);
        String searchPattern = null;
        if (searchTerm != null && !searchTerm.isBlank()) {
            searchPattern = "%" + StringUtil.escapeForLikeLowerCase(searchTerm) + "%";
        }

        Page<Long> idPage = presentationAssessmentInstanceRepository.findStudentRowIdsByCourseId(courseId, assessmentId, assessed, linkedToExercise, searchPattern, pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        var rowsById = presentationAssessmentInstanceRepository.findStudentRowsByInstanceIds(idPage.getContent()).stream()
                .collect(Collectors.toMap(row -> row.instance().id(), Function.identity()));
        var rows = idPage.getContent().stream().map(rowsById::get).filter(Objects::nonNull).toList();
        return new PageImpl<>(rows, pageable, idPage.getTotalElements());
    }

    private Pageable createStudentRowsPageable(int page, int size, String sortField, Sort.Direction direction) {
        String property = switch (sortField) {
            case "studentLogin" -> "student.login";
            case "presentationTitle" -> "presentationAssessment.title";
            case "presentationDate" -> "presentationDate";
            case "resultPoints" -> "resultPoints";
            default -> throw new BadRequestAlertException("Unsupported sort field", PresentationAssessmentInstance.ENTITY_NAME, "invalidSortField");
        };
        return PageRequest.of(page, size, Sort.by(direction, property).and(Sort.by("id")));
    }

    /**
     * Create a presentation assessment in a course.
     *
     * @param courseId the owning course id
     * @param dto      the presentation assessment data
     * @return the persisted presentation assessment as a DTO
     */
    public PresentationAssessmentDTO create(long courseId, PresentationAssessmentDTO dto) {
        if (dto.id() != null) {
            throw new BadRequestAlertException("A new presentation assessment cannot already have an ID", PresentationAssessment.ENTITY_NAME, "idExists");
        }
        PresentationAssessment presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(courseRepository.getReferenceById(courseId));
        ExerciseIdAndTitleDTO exercise = applyDto(presentationAssessment, dto);
        PresentationAssessment savedAssessment = presentationAssessmentRepository.save(presentationAssessment);

        return new PresentationAssessmentDTO(savedAssessment.getId(), savedAssessment.getTitle(), savedAssessment.getDescription(), savedAssessment.getMaxPoints(), courseId,
                exercise != null ? exercise.id() : null, exercise != null ? exercise.title() : null);
    }

    /**
     * Update a presentation assessment.
     *
     * @param courseId     the owning course id
     * @param assessmentId the presentation assessment id
     * @param dto          the updated presentation assessment data
     * @return the saved presentation assessment as a DTO
     */
    public PresentationAssessmentDTO update(long courseId, long assessmentId, PresentationAssessmentDTO dto) {
        if (dto.id() == null) {
            throw new BadRequestAlertException("A presentation assessment update must have an ID", PresentationAssessment.ENTITY_NAME, "idMissing");
        }
        if (!dto.id().equals(assessmentId)) {
            throw new BadRequestAlertException("The path id and body id must match", PresentationAssessment.ENTITY_NAME, "idMismatch");
        }
        PresentationAssessment presentationAssessment = findByIdAndCourseIdElseThrow(courseId, assessmentId);
        long expectedVersion = presentationAssessment.getVersion();

        double highestResultPoints = presentationAssessmentInstanceRepository.findHighestResultPointsByPresentationAssessmentId(assessmentId).orElse(0.0);
        if (dto.maxPoints() < highestResultPoints) {
            throw new BadRequestAlertException("The maximum points cannot be lower than an existing result", PresentationAssessment.ENTITY_NAME, "maxPointsBelowExistingResult");
        }
        ExerciseIdAndTitleDTO exercise = applyDto(presentationAssessment, dto);

        int updated = presentationAssessmentRepository.updateIfVersionMatches(assessmentId, courseId, expectedVersion, presentationAssessment.getTitle(),
                presentationAssessment.getDescription(), presentationAssessment.getMaxPoints(), presentationAssessment.getExercise());

        if (updated != 1) {
            throw new ConflictException("The presentation assessment changed while the update was being validated. Please reload and try again.",
                    PresentationAssessment.ENTITY_NAME, "concurrentModification");
        }

        return new PresentationAssessmentDTO(presentationAssessment.getId(), presentationAssessment.getTitle(), presentationAssessment.getDescription(),
                presentationAssessment.getMaxPoints(), courseId, exercise != null ? exercise.id() : null, exercise != null ? exercise.title() : null);
    }

    /**
     * Delete a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     */
    public void delete(long courseId, long assessmentId) {
        PresentationAssessment presentationAssessment = findByIdAndCourseIdElseThrow(courseId, assessmentId);
        presentationAssessmentRepository.delete(presentationAssessment);
    }

    /**
     * Updates an instance of a presentation assessment.
     *
     * @param courseId     the owning course id
     * @param assessmentId the parent presentation assessment id
     * @param instanceId   the presentation assessment instance id
     * @param dto          the updated instance data
     * @return the persisted presentation assessment instance
     */
    public PresentationAssessmentInstance updateInstance(long courseId, long assessmentId, long instanceId, PresentationAssessmentInstanceRequestDTO dto) {
        if (dto.id() == null || !dto.id().equals(instanceId)) {
            throw new BadRequestAlertException("The path id and body id must match", PresentationAssessmentInstance.ENTITY_NAME, "idMismatch");
        }
        PresentationAssessment assessment = findByIdAndCourseIdElseThrow(courseId, assessmentId);
        long expectedVersion = assessment.getVersion();

        PresentationAssessmentInstance instance = findInstanceElseThrow(courseId, assessmentId, instanceId);
        applyInstanceDto(courseId, assessment, instance, dto);

        presentationAssessmentInstanceRepository.updateInstanceIfVersionMatches(courseId, assessmentId, expectedVersion, instance);

        return findInstanceElseThrow(courseId, assessmentId, instanceId);
    }

    /**
     * Creates an individual presentation assessment instance for each selected student.
     *
     * @param courseId     the owning course id
     * @param assessmentId the parent presentation assessment id
     * @param dto          the creation request
     * @return the created presentation assessment instances
     */
    public List<PresentationAssessmentInstance> saveInstances(long courseId, long assessmentId, PresentationAssessmentInstancesBatchCreateDTO dto) {
        PresentationAssessment assessment = findByIdAndCourseIdElseThrow(courseId, assessmentId);
        long expectedVersion = assessment.getVersion();

        Set<User> students = resolveAssignedCourseStudents(courseId, dto.studentLogins());
        if (students.isEmpty()) {
            throw new BadRequestAlertException("At least one student must be selected", PresentationAssessmentInstance.ENTITY_NAME, "individualInstanceHasInvalidStudentCount");
        }
        rejectAlreadyAssignedStudents(assessmentId, students);

        User assessor = resolveAssessor(courseId, null, dto.assessorLogin());
        List<PresentationAssessmentInstance> instances = students.stream()
                .map(student -> createIndividualInstance(assessment, dto.forStudent(student.getLogin()), student, assessor)).toList();
        return presentationAssessmentInstanceRepository.createInstancesIfVersionMatches(courseId, assessmentId, expectedVersion, instances);
    }

    /**
     * Deletes an instance belonging to the given presentation assessment and course.
     *
     * @param courseId     the owning course id
     * @param assessmentId the presentation assessment id
     * @param instanceId   the instance id
     */
    public void deleteInstance(long courseId, long assessmentId, long instanceId) {
        PresentationAssessment assessment = findByIdAndCourseIdElseThrow(courseId, assessmentId);
        long expectedVersion = assessment.getVersion();
        PresentationAssessmentInstance instance = findInstanceElseThrow(courseId, assessmentId, instanceId);
        presentationAssessmentInstanceRepository.deleteInstanceIfVersionMatches(courseId, assessmentId, expectedVersion, instance);
    }

    private PresentationAssessmentInstance findInstanceElseThrow(long courseId, long assessmentId, long instanceId) {
        return presentationAssessmentInstanceRepository.findByIdAndPresentationAssessmentIdAndPresentationAssessmentCourseId(instanceId, assessmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException(PresentationAssessmentInstance.ENTITY_NAME, instanceId));
    }

    private void applyInstanceDto(long courseId, PresentationAssessment assessment, PresentationAssessmentInstance instance, PresentationAssessmentInstanceRequestDTO dto) {
        // The course membership is only checked when the presenter changes, so a grade or remark can still be recorded for a student who left the course.
        boolean presenterUnchanged = dto.studentLogin() != null && dto.studentLogin().trim().equals(instance.getStudent().getLogin());
        User student = presenterUnchanged ? instance.getStudent() : resolveAssignedCourseStudent(courseId, dto.studentLogin());
        if (!presenterUnchanged
                && presentationAssessmentInstanceRepository.existsByPresentationAssessmentIdAndStudentIdAndIdNot(assessment.getId(), student.getId(), instance.getId())) {
            throwStudentAlreadyAssigned(List.of(student.getLogin()));
        }
        applyInstanceData(assessment, instance, dto);
        instance.setStudent(student);
        instance.setAssessor(resolveAssessor(courseId, instance.getAssessor(), dto.assessorLogin()));
    }

    /**
     * Editors choose any tutor, editor or instructor of the course as the assessor; like the presenter, an unchanged assessor is kept even if they left the course.
     * A tutor always assesses as themselves and cannot edit an instance that someone else assesses. Like the assessor of a result, anyone allowed to grade may
     * assess as themselves, so an administrator without a course role can too.
     *
     * @param courseId        the owning course id
     * @param currentAssessor the assessor of the instance being updated, or null for a new instance or one without an assessor
     * @param assessorLogin   the requested assessor login
     * @return the assessor to store
     */
    private User resolveAssessor(long courseId, User currentAssessor, String assessorLogin) {
        String login = assessorLogin.trim();
        boolean isAtLeastEditor = authorizationCheckService.isAtLeastEditorInCourse(courseId);
        if (isAtLeastEditor && currentAssessor != null && currentAssessor.getLogin().equals(login)) {
            return currentAssessor;
        }
        User currentUser = userRepository.getUser();
        if (!isAtLeastEditor) {
            boolean assessedBySomeoneElse = currentAssessor != null && !currentAssessor.getId().equals(currentUser.getId());
            if (assessedBySomeoneElse || !currentUser.getLogin().equals(login)) {
                throw new AccessForbiddenException("Tutors only assess presentations as themselves and cannot edit presentations assessed by someone else");
            }
            return currentUser;
        }
        if (currentUser.getLogin().equals(login)) {
            return currentUser;
        }
        return userRepository.findOneByCourseIdAndCourseRolesInAndLogin(courseId, ASSESSOR_ROLES, login).orElseThrow(
                () -> new BadRequestAlertException("The assessor must be at least a tutor in the course", PresentationAssessmentInstance.ENTITY_NAME, "assessorNotInCourse"));
    }

    /**
     * A student presents a presentation once, so selecting a student who already has an instance of it is rejected instead of creating a second one.
     * The check is not racy: both writes read the presentation version before this check and only one of them can advance it.
     */
    private void rejectAlreadyAssignedStudents(long assessmentId, Set<User> students) {
        List<String> assignedLogins = presentationAssessmentInstanceRepository.findAssignedStudentLogins(assessmentId, students.stream().map(User::getId).toList());
        if (!assignedLogins.isEmpty()) {
            throwStudentAlreadyAssigned(assignedLogins);
        }
    }

    private void throwStudentAlreadyAssigned(List<String> logins) {
        String joinedLogins = String.join(", ", logins);
        throw new BadRequestAlertException("These students already have an instance of this presentation: " + joinedLogins, PresentationAssessmentInstance.ENTITY_NAME,
                "studentAlreadyAssigned", Map.of("logins", joinedLogins));
    }

    private void applyInstanceData(PresentationAssessment assessment, PresentationAssessmentInstance instance, PresentationAssessmentInstanceRequestDTO dto) {
        if (dto.resultPoints() != null && dto.resultPoints() > assessment.getMaxPoints()) {
            throw new BadRequestAlertException("The achieved result points cannot exceed the maximum points", PresentationAssessmentInstance.ENTITY_NAME,
                    "resultPointsExceedMaxPoints");
        }
        if (dto.presentationDate().toLocalDate().isBefore(EARLIEST_PRESENTATION_DATE)) {
            throw new BadRequestAlertException("The presentation date cannot be before 1970-01-01", PresentationAssessmentInstance.ENTITY_NAME, "presentationDateBeforeUnixEpoch");
        }
        instance.setPresentationDate(dto.presentationDate());
        instance.setResultPoints(dto.resultPoints());
        instance.setLanguage(dto.language());
        instance.setMode(dto.mode());
        instance.setLocation(dto.mode() == PresentationAssessmentMode.IN_PERSON ? dto.location() : null);
        instance.setMeetingLink(dto.mode() == PresentationAssessmentMode.ONLINE ? dto.meetingLink() : null);
        instance.setRemark(dto.remark());
    }

    private PresentationAssessmentInstance createIndividualInstance(PresentationAssessment assessment, PresentationAssessmentInstanceRequestDTO dto, User student, User assessor) {
        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(assessment);
        applyInstanceData(assessment, instance, dto);
        instance.setStudent(student);
        instance.setAssessor(assessor);
        return instance;
    }

    private ExerciseIdAndTitleDTO applyDto(PresentationAssessment presentationAssessment, PresentationAssessmentDTO dto) {
        ExerciseIdAndTitleDTO exercise = findCourseExercise(presentationAssessment.getCourse().getId(), dto.exerciseId());

        presentationAssessment.setTitle(dto.title().trim());
        presentationAssessment.setDescription(dto.description());
        presentationAssessment.setMaxPoints(dto.maxPoints());
        presentationAssessment.setExercise(exercise == null ? null : exerciseRepository.getReferenceById(exercise.id()));

        return exercise;
    }

    private ExerciseIdAndTitleDTO findCourseExercise(long courseId, Long exerciseId) {
        if (exerciseId == null) {
            return null;
        }
        return exerciseRepository.findIdAndTitleByIdAndCourseId(exerciseId, courseId).orElseThrow(() -> {
            if (!exerciseRepository.existsById(exerciseId)) {
                return new EntityNotFoundException("Exercise", exerciseId);
            }
            return new BadRequestAlertException("The exercise must belong directly to the course", PresentationAssessment.ENTITY_NAME, "exerciseNotInCourse");
        });
    }

    private User resolveAssignedCourseStudent(long courseId, String studentLogin) {
        if (studentLogin == null || studentLogin.isBlank()) {
            throw new BadRequestAlertException("A student login must not be empty", PresentationAssessment.ENTITY_NAME, "studentLoginInvalid");
        }

        return userRepository.findAllByCourseIdAndRoleAndLoginIn(courseId, CourseRole.STUDENT, Set.of(studentLogin.trim())).stream().findFirst()
                .orElseThrow(() -> new BadRequestAlertException("The user is not a student in the course", PresentationAssessment.ENTITY_NAME, "studentNotInCourse"));
    }

    private Set<User> resolveAssignedCourseStudents(long courseId, List<String> studentLogins) {
        if (studentLogins == null || studentLogins.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> uniqueLogins = new HashSet<>();
        for (String login : studentLogins) {
            if (login == null || login.isBlank()) {
                throw new BadRequestAlertException("A student login must not be empty", PresentationAssessment.ENTITY_NAME, "studentLoginInvalid");
            }
            uniqueLogins.add(login.trim());
        }
        Set<User> students = new HashSet<>(userRepository.findAllByCourseIdAndRoleAndLoginIn(courseId, CourseRole.STUDENT, uniqueLogins));
        Set<String> foundLogins = students.stream().map(User::getLogin).collect(Collectors.toSet());
        if (!foundLogins.containsAll(uniqueLogins)) {
            throw new BadRequestAlertException("At least one user is not a student in the course", PresentationAssessment.ENTITY_NAME, "studentNotInCourse");
        }
        return students;
    }
}
