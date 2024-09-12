package de.tum.cit.aet.artemis.exam.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamImportService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;

/**
 * REST controller for managing ExerciseGroup.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ExerciseGroupResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGroupResource.class);

    private static final String ENTITY_NAME = "exerciseGroup";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final ExamRepository examRepository;

    private final ExamAccessService examAccessService;

    private final UserRepository userRepository;

    private final ExerciseDeletionService exerciseDeletionService;

    private final AuditEventRepository auditEventRepository;

    private final ExamImportService examImportService;

    public ExerciseGroupResource(ExerciseGroupRepository exerciseGroupRepository, ExamAccessService examAccessService, UserRepository userRepository,
            ExerciseDeletionService exerciseDeletionService, AuditEventRepository auditEventRepository, ExamRepository examRepository, ExamImportService examImportService) {
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examRepository = examRepository;
        this.examAccessService = examAccessService;
        this.userRepository = userRepository;
        this.exerciseDeletionService = exerciseDeletionService;
        this.auditEventRepository = auditEventRepository;
        this.examImportService = examImportService;
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/exerciseGroups : Create a new exercise group.
     *
     * @param courseId      the course to which the exercise group belongs to
     * @param examId        the exam to which the exercise group belongs to
     * @param exerciseGroup the exercise group to create
     * @return the ResponseEntity with status 201 (Created) and with the new exerciseGroup as body,
     *         or with status 400 (Bad Request) if the exerciseGroup has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exerciseGroups")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseGroup> createExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody ExerciseGroup exerciseGroup)
            throws URISyntaxException {
        log.debug("REST request to create an exercise group : {}", exerciseGroup);
        if (exerciseGroup.getId() != null) {
            throw new BadRequestAlertException("A new exerciseGroup cannot already have an ID", ENTITY_NAME, "idExists");
        }

        if (exerciseGroup.getExam() == null) {
            throw new ConflictException("The exercise group has to belong no an exam.", ENTITY_NAME, "missingExam");
        }

        if (!exerciseGroup.getExam().getId().equals(examId)) {
            throw new ConflictException("The exam connected to this group does not have the given exam id.", ENTITY_NAME, "wrongExamId");
        }

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        // Save the exerciseGroup as part of the exam to ensure that the order column is set correctly
        Exam examFromDB = examRepository.findByIdWithExerciseGroupsElseThrow(examId);
        examFromDB.addExerciseGroup(exerciseGroup);
        Exam savedExam = examRepository.save(examFromDB);
        ExerciseGroup savedExerciseGroup = savedExam.getExerciseGroups().getLast();

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examId + "/exerciseGroups/" + savedExerciseGroup.getId())).body(savedExerciseGroup);
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/exerciseGroups : Update an existing exercise group.
     *
     * @param courseId             the course to which the exercise group belongs to
     * @param examId               the exam to which the exercise group belongs to
     * @param updatedExerciseGroup the exercise group to update
     * @return the ResponseEntity with status 200 (OK) and with the body of the updated exercise group
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("courses/{courseId}/exams/{examId}/exerciseGroups")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseGroup> updateExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody ExerciseGroup updatedExerciseGroup)
            throws URISyntaxException {
        log.debug("REST request to update an exercise group : {}", updatedExerciseGroup);
        if (updatedExerciseGroup.getId() == null) {
            return createExerciseGroup(courseId, examId, updatedExerciseGroup);
        }

        if (updatedExerciseGroup.getExam() == null) {
            throw new ConflictException("The exercise group has to belong to an exam.", ENTITY_NAME, "missingExam");
        }

        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, courseId, examId, updatedExerciseGroup);

        ExerciseGroup result = exerciseGroupRepository.save(updatedExerciseGroup);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/import-exercise-group : Imports exercise groups to the specified exam
     *
     * @param courseId             the course to which the exam belongs
     * @param examId               the exam to which the exercise groups should be added
     * @param updatedExerciseGroup the list of Exercise Groups to be imported
     * @return the ResponseEntity with status 201 (Created) and with body the newly imported exercise groups, or with status 400 (Bad Request)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/import-exercise-group")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ExerciseGroup>> importExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<ExerciseGroup> updatedExerciseGroup)
            throws IOException {
        log.debug("REST request to import {} exercise group(s) to exam {}", updatedExerciseGroup.size(), examId);

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        List<ExerciseGroup> result = examImportService.importExerciseGroupsWithExercisesToExistingExam(updatedExerciseGroup, examId, courseId);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/exerciseGroups/{exerciseGroupId} : Find an exercise group by id.
     *
     * @param courseId        the course to which the exercise group belongs to
     * @param examId          the exam to which the exercise group belongs to
     * @param exerciseGroupId the id of the exercise group to find
     * @return the ResponseEntity with status 200 (OK) and with the found exercise group as body
     */
    @GetMapping("courses/{courseId}/exams/{examId}/exerciseGroups/{exerciseGroupId}")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseGroup> getExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long exerciseGroupId) {
        log.debug("REST request to get exercise group : {}", exerciseGroupId);

        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdElseThrow(exerciseGroupId);
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, courseId, examId, exerciseGroup);

        return ResponseEntity.ok(exerciseGroup);
    }

    /**
     * GET courses/{courseId}/exams/{examId}/exerciseGroups : Get all exercise groups of the given exam
     *
     * @param courseId the course to which the exercise groups belong to
     * @param examId   the exam to which the exercise groups belong to
     * @return the ResponseEntity with status 200 (OK) and a list of exercise groups. The list can be empty
     */
    @GetMapping("courses/{courseId}/exams/{examId}/exerciseGroups")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ExerciseGroup>> getExerciseGroupsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all exercise groups for exam : {}", examId);

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        List<ExerciseGroup> exerciseGroupList = exerciseGroupRepository.findWithExamAndExercisesByExamId(examId);
        return ResponseEntity.ok(exerciseGroupList);
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/exerciseGroups/{exerciseGroupId} : Delete the exercise group with the given id.
     *
     * @param courseId                     the course to which the exercise group belongs to
     * @param examId                       the exam to which the exercise group belongs to
     * @param exerciseGroupId              the id of the exercise group to delete
     * @param deleteStudentReposBuildPlans boolean which states whether the student repos and build plans should be deleted as well, this is true by default because for LocalVC
     *                                         and LocalCI, it does not make sense to keep these artifacts
     * @param deleteBaseReposBuildPlans    boolean which states whether the base repos and build plans should be deleted as well, this is true by default because for LocalVC and
     *                                         LocalCI, it does not make sense to keep these artifacts
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/exerciseGroups/{exerciseGroupId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long exerciseGroupId,
            @RequestParam(defaultValue = "true") boolean deleteStudentReposBuildPlans, @RequestParam(defaultValue = "true") boolean deleteBaseReposBuildPlans) {
        log.info("REST request to delete exercise group : {}", exerciseGroupId);

        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdWithExercisesElseThrow(exerciseGroupId);
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, courseId, examId, exerciseGroup);

        User user = userRepository.getUser();
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXERCISE_GROUP, "exerciseGroup=" + exerciseGroup.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete the exercise group {}", user.getLogin(), exerciseGroup.getTitle());

        for (Exercise exercise : exerciseGroup.getExercises()) {
            exerciseDeletionService.delete(exercise.getId(), deleteStudentReposBuildPlans, deleteBaseReposBuildPlans);
        }

        // Remove the exercise group by removing it from the list of exercise groups of the corresponding exam.
        // This is necessary as @OrderColumn (exercise_group_order) needs continuous values. Otherwise the client will
        // receive null values for the gaps in exam.getExerciseGroups().
        Exam exam = examRepository.findByIdWithExerciseGroupsElseThrow(examId);
        List<ExerciseGroup> filteredExerciseGroups = exam.getExerciseGroups();
        filteredExerciseGroups.removeIf(exGroup -> exGroup.getId().equals(exerciseGroupId));
        exam.setExerciseGroups(filteredExerciseGroups);
        examRepository.save(exam);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exerciseGroup.getTitle())).build();
    }
}
