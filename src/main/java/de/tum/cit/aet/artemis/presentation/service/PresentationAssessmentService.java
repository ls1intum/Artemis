package de.tum.cit.aet.artemis.presentation.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentInstanceDTO;
import de.tum.cit.aet.artemis.presentation.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.presentation.repository.PresentationAssessmentRepository;

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

    public PresentationAssessmentService(PresentationAssessmentRepository presentationAssessmentRepository, UserRepository userRepository, ExerciseRepository exerciseRepository,
            PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.presentationAssessmentInstanceRepository = presentationAssessmentInstanceRepository;
    }

    private PresentationAssessment findByIdAndCourseIdElseThrow(long courseId, long assessmentId) {
        return presentationAssessmentRepository.findByIdAndCourseIdElseThrow(assessmentId, courseId);
    }

    /**
     * Create a presentation assessment in a course.
     *
     * @param course the owning course
     * @param dto    the presentation assessment data
     * @return the persisted presentation assessment
     */
    public PresentationAssessment create(Course course, PresentationAssessmentDTO dto) {
        if (dto.id() != null) {
            throw new BadRequestAlertException("A new presentation assessment cannot already have an ID", PresentationAssessment.ENTITY_NAME, "idExists");
        }
        PresentationAssessment presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(course);
        Set<User> students = resolveAssignedCourseStudents(course, dto.studentLogins());
        applyDto(presentationAssessment, dto);
        presentationAssessment.setStudents(students);
        return presentationAssessmentRepository.save(presentationAssessment);
    }

    /**
     * Update a presentation assessment.
     *
     * @param course       the owning course
     * @param assessmentId the presentation assessment id
     * @param dto          the updated presentation assessment data
     * @return the persisted presentation assessment data
     */
    public PresentationAssessmentDTO update(Course course, long assessmentId, PresentationAssessmentDTO dto) {
        if (dto.id() == null) {
            throw new BadRequestAlertException("A presentation assessment update must have an ID", PresentationAssessment.ENTITY_NAME, "idMissing");
        }
        if (!dto.id().equals(assessmentId)) {
            throw new BadRequestAlertException("The path id and body id must match", PresentationAssessment.ENTITY_NAME, "idMismatch");
        }
        Set<User> students = dto.studentLogins() != null ? resolveAssignedCourseStudents(course, dto.studentLogins()) : null;
        PresentationAssessment presentationAssessment = findByIdAndCourseIdElseThrow(course.getId(), assessmentId);
        applyDto(presentationAssessment, dto);
        if (students != null) {
            presentationAssessment.setStudents(students);
        }
        PresentationAssessment savedPresentationAssessment = presentationAssessmentRepository.save(presentationAssessment);
        return PresentationAssessmentDTO.of(savedPresentationAssessment);
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
     * Creates an instance of a presentation assessment.
     *
     * @param course       the owning course
     * @param assessmentId the parent presentation assessment id
     * @param dto          the instance data
     * @return the persisted presentation assessment instance
     */
    public PresentationAssessmentInstance createInstance(Course course, long assessmentId, PresentationAssessmentInstanceDTO dto) {
        if (dto.id() != null) {
            throw new BadRequestAlertException("A new presentation instance cannot already have an ID", PresentationAssessmentInstance.ENTITY_NAME, "idExists");
        }
        PresentationAssessment assessment = findByIdAndCourseIdElseThrow(course.getId(), assessmentId);
        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(assessment);
        applyInstanceDto(course, assessment, instance, dto);
        return presentationAssessmentInstanceRepository.save(instance);
    }

    /**
     * Updates an instance of a presentation assessment.
     *
     * @param course       the owning course
     * @param assessmentId the parent presentation assessment id
     * @param instanceId   the presentation assessment instance id
     * @param dto          the updated instance data
     * @return the persisted presentation assessment instance
     */
    public PresentationAssessmentInstance updateInstance(Course course, long assessmentId, long instanceId, PresentationAssessmentInstanceDTO dto) {
        if (dto.id() == null || !dto.id().equals(instanceId)) {
            throw new BadRequestAlertException("The path id and body id must match", PresentationAssessmentInstance.ENTITY_NAME, "idMismatch");
        }
        PresentationAssessmentInstance instance = findInstanceElseThrow(course.getId(), assessmentId, instanceId);
        applyInstanceDto(course, instance.getPresentationAssessment(), instance, dto);
        return presentationAssessmentInstanceRepository.save(instance);
    }

    public void deleteInstance(long courseId, long assessmentId, long instanceId) {
        presentationAssessmentInstanceRepository.delete(findInstanceElseThrow(courseId, assessmentId, instanceId));
    }

    private PresentationAssessmentInstance findInstanceElseThrow(long courseId, long assessmentId, long instanceId) {
        return presentationAssessmentInstanceRepository.findByIdAndPresentationAssessmentIdAndPresentationAssessmentCourseId(instanceId, assessmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException(PresentationAssessmentInstance.ENTITY_NAME, instanceId));
    }

    private void applyInstanceDto(Course course, PresentationAssessment assessment, PresentationAssessmentInstance instance, PresentationAssessmentInstanceDTO dto) {
        if (dto.resultPoints() != null && dto.resultPoints() > assessment.getMaxPoints()) {
            throw new BadRequestAlertException("The achieved result points cannot exceed the maximum points", PresentationAssessmentInstance.ENTITY_NAME,
                    "resultPointsExceedMaxPoints");
        }
        if (dto.presentationDate().toLocalDate().isBefore(EARLIEST_PRESENTATION_DATE)) {
            throw new BadRequestAlertException("The presentation date cannot be before 1970-01-01", PresentationAssessmentInstance.ENTITY_NAME, "presentationDateBeforeUnixEpoch");
        }
        instance.setPresentationDate(dto.presentationDate());
        instance.setResultPoints(dto.resultPoints());
        instance.setStudents(resolveAssignedCourseStudents(course, dto.studentLogins()));
        instance.setLanguage(dto.language());
        instance.setMode(dto.mode());
        instance.setLocation(dto.mode() == de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentMode.IN_PERSON ? dto.location() : null);
        instance.setMeetingLink(dto.mode() == de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentMode.ONLINE ? dto.meetingLink() : null);
        instance.setRemark(dto.remark());
    }

    private void applyDto(PresentationAssessment presentationAssessment, PresentationAssessmentDTO dto) {
        if (dto.resultPoints() != null && dto.resultPoints() > dto.maxPoints()) {
            throw new BadRequestAlertException("The achieved result points cannot exceed the maximum points", PresentationAssessment.ENTITY_NAME, "resultPointsExceedMaxPoints");
        }
        presentationAssessment.setTitle(dto.title().trim());
        presentationAssessment.setDescription(dto.description());
        presentationAssessment.setMaxPoints(dto.maxPoints());
        presentationAssessment.setResultPoints(dto.resultPoints());
        presentationAssessment.setPresentationDate(dto.presentationDate());
        presentationAssessment.setExercise(findCourseExercise(presentationAssessment.getCourse(), dto.exerciseId()));
    }

    private Exercise findCourseExercise(Course course, Long exerciseId) {
        if (exerciseId == null) {
            return null;
        }
        Exercise exercise = exerciseRepository.findByIdWithExerciseGroupExamAndCourse(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
        Course exerciseCourse = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (exerciseCourse == null || !exerciseCourse.getId().equals(course.getId()) || exercise.getExerciseGroup() != null) {
            throw new BadRequestAlertException("The exercise must belong directly to the course", PresentationAssessment.ENTITY_NAME, "exerciseNotInCourse");
        }
        return exercise;
    }

    private Set<User> resolveAssignedCourseStudents(Course course, List<String> studentLogins) {
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
        Set<User> students = new HashSet<>(userRepository.findAllByCourseIdAndRoleAndLoginIn(course.getId(), CourseRole.STUDENT, uniqueLogins));
        Set<String> foundLogins = students.stream().map(User::getLogin).collect(Collectors.toSet());
        if (!foundLogins.containsAll(uniqueLogins)) {
            throw new BadRequestAlertException("At least one user is not a student in the course", PresentationAssessment.ENTITY_NAME, "studentNotInCourse");
        }
        return students;
    }
}
