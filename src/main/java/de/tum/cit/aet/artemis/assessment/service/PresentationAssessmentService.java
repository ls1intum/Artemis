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

import org.jspecify.annotations.Nullable;
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
import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstancesBatchCreateDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStatisticsDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStudentRowDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricRatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricWriteRepository.PresentationRubricChanges;
import de.tum.cit.aet.artemis.assessment.service.PresentationRubricService.RubricGrade;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
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

    private final PresentationAssessmentRepository presentationAssessmentRepository;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    private final CourseRepository courseRepository;

    private final CourseConfigurationRepository courseConfigurationRepository;

    private final PresentationRubricService presentationRubricService;

    private final PresentationRubricCriterionRepository presentationRubricCriterionRepository;

    private final PresentationRubricRatingRepository presentationRubricRatingRepository;

    public PresentationAssessmentService(PresentationAssessmentRepository presentationAssessmentRepository, UserRepository userRepository, ExerciseRepository exerciseRepository,
            PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository, CourseRepository courseRepository,
            CourseConfigurationRepository courseConfigurationRepository, PresentationRubricService presentationRubricService,
            PresentationRubricCriterionRepository presentationRubricCriterionRepository, PresentationRubricRatingRepository presentationRubricRatingRepository) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.presentationAssessmentInstanceRepository = presentationAssessmentInstanceRepository;
        this.courseRepository = courseRepository;
        this.courseConfigurationRepository = courseConfigurationRepository;
        this.presentationRubricService = presentationRubricService;
        this.presentationRubricCriterionRepository = presentationRubricCriterionRepository;
        this.presentationRubricRatingRepository = presentationRubricRatingRepository;
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

    private PresentationAssessment findByIdAndCourseIdElseThrow(long courseId, long assessmentId) {
        return presentationAssessmentRepository.findOneByIdAndCourseId(assessmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, assessmentId));
    }

    /**
     * Loads a filtered page of individual presentation assessment rows.
     *
     * @param courseId         the owning course id
     * @param assessmentId     the presentation assessment id, or null for no filter
     * @param assessed         whether a result is assigned, or null for no filter
     * @param linkedToExercise whether the presentation is linked to an exercise, or null for no filter
     * @param searchTerm       text to search in student login, email, full name, or presentation title; null or blank disables searching
     * @param page             zero-based page index
     * @param size             page size between 1 and 100
     * @param sortField        studentLogin, presentationTitle, or presentationDate
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
        presentationRubricService.validateRubricPresence(dto.gradingMethod(), dto.rubricCriteria(), true);
        PresentationAssessment presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(courseRepository.getReferenceById(courseId));
        presentationAssessment.setGradingMethod(dto.gradingMethod());
        ExerciseIdAndTitleDTO exercise = applyDto(presentationAssessment, dto);
        PresentationRubricChanges rubric = dto.rubricCriteria() == null ? PresentationRubricChanges.NONE
                : presentationRubricService.buildReplacement(presentationAssessment, dto.rubricCriteria());
        PresentationAssessment savedAssessment = presentationRubricCriterionRepository.createWithRubric(presentationAssessment, rubric);

        return toDto(savedAssessment, courseId, exercise);
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
        if (dto.gradingMethod() != presentationAssessment.getGradingMethod()) {
            throw new BadRequestAlertException("The grading method of a presentation cannot be changed", PresentationAssessment.ENTITY_NAME, "gradingMethodImmutable");
        }
        long expectedVersion = presentationAssessment.getVersion();
        ExerciseIdAndTitleDTO exercise = applyDto(presentationAssessment, dto);

        if (presentationAssessment.getGradingMethod() == PresentationGradingMethod.SCORE) {
            double highestResultPoints = presentationAssessmentInstanceRepository.findHighestResultPointsByPresentationAssessmentId(assessmentId).orElse(0.0);
            if (presentationAssessment.getMaxPoints() < highestResultPoints) {
                throw new BadRequestAlertException("The maximum points cannot be lower than an existing result", PresentationAssessment.ENTITY_NAME,
                        "maxPointsBelowExistingResult");
            }
        }

        presentationRubricService.validateRubricPresence(presentationAssessment.getGradingMethod(), dto.rubricCriteria(), false);
        PresentationRubricChanges rubric = PresentationRubricChanges.NONE;
        if (dto.rubricCriteria() != null) {
            boolean locked = presentationAssessmentInstanceRepository.countByPresentationAssessmentId(assessmentId) > 0;
            rubric = locked ? presentationRubricService.buildLockedUpdate(assessmentId, dto.rubricCriteria())
                    : presentationRubricService.buildReplacement(presentationAssessment, dto.rubricCriteria());
        }
        presentationRubricCriterionRepository.updateWithRubricIfVersionMatches(courseId, assessmentId, expectedVersion, presentationAssessment, rubric);

        return toDto(presentationAssessment, courseId, exercise);
    }

    private static PresentationAssessmentDTO toDto(PresentationAssessment presentationAssessment, long courseId, ExerciseIdAndTitleDTO exercise) {
        return new PresentationAssessmentDTO(presentationAssessment.getId(), presentationAssessment.getTitle(), presentationAssessment.getDescription(),
                presentationAssessment.getGradingMethod(), presentationAssessment.getMaxPoints(), courseId, exercise != null ? exercise.id() : null,
                exercise != null ? exercise.title() : null);
    }

    /**
     * Delete a presentation assessment.
     * Its instances and its rubric with all ratings are removed by the database ({@code ON DELETE CASCADE}).
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
        RubricGrade grade = gradeRubric(assessment, dto.ratings());
        applyInstanceDto(courseId, assessment, instance, dto, grade);

        List<PresentationRubricRating> ratings = grade == null ? null : grade.ratingsFor(instance);
        presentationAssessmentInstanceRepository.updateInstanceIfVersionMatches(courseId, assessmentId, expectedVersion, instance, ratings);

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
        RubricGrade grade = gradeRubric(assessment, dto.ratings());

        List<PresentationAssessmentInstance> instances = students.stream().map(student -> createIndividualInstance(assessment, dto.forStudent(student.getLogin()), student, grade))
                .toList();
        List<PresentationRubricRating> ratings = grade == null ? List.of() : instances.stream().flatMap(instance -> grade.ratingsFor(instance).stream()).toList();
        return presentationAssessmentInstanceRepository.createInstancesIfVersionMatches(courseId, assessmentId, expectedVersion, instances, ratings);
    }

    /**
     * Loads the ratings of an instance, so that an assessed instance can be opened again.
     *
     * @param courseId     the owning course id
     * @param assessmentId the presentation assessment id
     * @param instanceId   the instance id
     * @return the ratings of the instance, in the order of the rubric
     */
    public List<PresentationRubricRatingDTO> getRatings(long courseId, long assessmentId, long instanceId) {
        findInstanceElseThrow(courseId, assessmentId, instanceId);
        return presentationRubricRatingRepository.findAllByInstanceIdOrderByCriterionPosition(instanceId).stream().map(PresentationRubricRatingDTO::of).toList();
    }

    /**
     * Validates the ratings of a request once, so that a batch does not repeat it for every student.
     * Ratings for another grading method are rejected later together with the other results.
     */
    private @Nullable RubricGrade gradeRubric(PresentationAssessment assessment, @Nullable List<PresentationRubricRatingDTO> ratings) {
        if (ratings == null || assessment.getGradingMethod() != PresentationGradingMethod.RUBRIC) {
            return null;
        }
        return presentationRubricService.grade(assessment.getId(), ratings);
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

    private void applyInstanceDto(long courseId, PresentationAssessment assessment, PresentationAssessmentInstance instance, PresentationAssessmentInstanceRequestDTO dto,
            @Nullable RubricGrade grade) {
        // The course membership is only checked when the presenter changes, so a grade or remark can still be recorded for a student who left the course.
        boolean presenterUnchanged = dto.studentLogin() != null && dto.studentLogin().trim().equals(instance.getStudent().getLogin());
        User student = presenterUnchanged ? instance.getStudent() : resolveAssignedCourseStudent(courseId, dto.studentLogin());
        if (!presenterUnchanged
                && presentationAssessmentInstanceRepository.existsByPresentationAssessmentIdAndStudentIdAndIdNot(assessment.getId(), student.getId(), instance.getId())) {
            throwStudentAlreadyAssigned(List.of(student.getLogin()));
        }
        applyInstanceData(assessment, instance, dto, grade);
        instance.setStudent(student);
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

    private void applyInstanceData(PresentationAssessment assessment, PresentationAssessmentInstance instance, PresentationAssessmentInstanceRequestDTO dto,
            @Nullable RubricGrade grade) {
        if (dto.presentationDate().toLocalDate().isBefore(EARLIEST_PRESENTATION_DATE)) {
            throw new BadRequestAlertException("The presentation date cannot be before 1970-01-01", PresentationAssessmentInstance.ENTITY_NAME, "presentationDateBeforeUnixEpoch");
        }
        applyResult(assessment, instance, dto, grade);
        instance.setPresentationDate(dto.presentationDate());
        instance.setLanguage(dto.language());
        instance.setMode(dto.mode());
        instance.setLocation(dto.mode() == PresentationAssessmentMode.IN_PERSON ? dto.location() : null);
        instance.setMeetingLink(dto.mode() == PresentationAssessmentMode.ONLINE ? dto.meetingLink() : null);
        instance.setRemark(dto.remark());
    }

    private void applyResult(PresentationAssessment assessment, PresentationAssessmentInstance instance, PresentationAssessmentInstanceRequestDTO dto,
            @Nullable RubricGrade grade) {
        boolean hadResult = instance.getResultPoints() != null || instance.getPassed() != null || instance.getRubricScore() != null;
        switch (assessment.getGradingMethod()) {
            case PASS_FAIL -> {
                rejectResultOfOtherMethod(dto.resultPoints() != null || dto.ratings() != null);
                rejectRemovedResult(hadResult, dto.passed() != null);
                instance.setPassed(dto.passed());
            }
            case SCORE -> {
                rejectResultOfOtherMethod(dto.passed() != null || dto.ratings() != null);
                rejectRemovedResult(hadResult, dto.resultPoints() != null);
                if (dto.resultPoints() != null && dto.resultPoints() > assessment.getMaxPoints()) {
                    throw new BadRequestAlertException("The achieved result points cannot exceed the maximum points", PresentationAssessmentInstance.ENTITY_NAME,
                            "resultPointsExceedMaxPoints");
                }
                instance.setResultPoints(dto.resultPoints());
            }
            case RUBRIC -> {
                rejectResultOfOtherMethod(dto.passed() != null || dto.resultPoints() != null);
                rejectRemovedResult(hadResult, grade != null);
                instance.setRubricScore(grade == null ? null : grade.score());
            }
        }
    }

    private static void rejectResultOfOtherMethod(boolean hasResultOfOtherMethod) {
        if (hasResultOfOtherMethod) {
            throw new BadRequestAlertException("The result does not match the grading method of the presentation", PresentationAssessmentInstance.ENTITY_NAME,
                    "resultDoesNotMatchGradingMethod");
        }
    }

    private static void rejectRemovedResult(boolean hadResult, boolean hasResult) {
        if (hadResult && !hasResult) {
            throw new BadRequestAlertException("An assigned result cannot be removed", PresentationAssessmentInstance.ENTITY_NAME, "resultCannotBeRemoved");
        }
    }

    private PresentationAssessmentInstance createIndividualInstance(PresentationAssessment assessment, PresentationAssessmentInstanceRequestDTO dto, User student,
            @Nullable RubricGrade grade) {
        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(assessment);
        applyInstanceData(assessment, instance, dto, grade);
        instance.setStudent(student);
        return instance;
    }

    private ExerciseIdAndTitleDTO applyDto(PresentationAssessment presentationAssessment, PresentationAssessmentDTO dto) {
        boolean gradedByScore = presentationAssessment.getGradingMethod() == PresentationGradingMethod.SCORE;
        if (gradedByScore && dto.maxPoints() == null) {
            throw new BadRequestAlertException("A presentation graded by score needs maximum points", PresentationAssessment.ENTITY_NAME, "maxPointsRequired");
        }
        if (!gradedByScore && dto.maxPoints() != null) {
            throw new BadRequestAlertException("Only a presentation graded by score has maximum points", PresentationAssessment.ENTITY_NAME, "maxPointsNotAllowed");
        }
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
