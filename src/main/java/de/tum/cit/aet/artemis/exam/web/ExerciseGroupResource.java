package de.tum.cit.aet.artemis.exam.web;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamExerciseGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupCreateDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupImportResultDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupUpdateDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamImportService;
import de.tum.cit.aet.artemis.exam.service.ExerciseGroupService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;

/**
 * REST controller for managing ExerciseGroup.
 */
@Conditional(ExamEnabled.class)
@Lazy
@FeatureUsage("authoring/exercise-groups")
@RestController
@RequestMapping("api/exam/")
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

    private final ExerciseRepository exerciseRepository;

    private final ExerciseGroupService exerciseGroupService;

    public ExerciseGroupResource(ExerciseGroupRepository exerciseGroupRepository, ExamAccessService examAccessService, UserRepository userRepository,
            ExerciseDeletionService exerciseDeletionService, AuditEventRepository auditEventRepository, ExamRepository examRepository, ExamImportService examImportService,
            ExerciseRepository exerciseRepository, ExerciseGroupService exerciseGroupService) {
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examRepository = examRepository;
        this.examAccessService = examAccessService;
        this.userRepository = userRepository;
        this.exerciseDeletionService = exerciseDeletionService;
        this.auditEventRepository = auditEventRepository;
        this.examImportService = examImportService;
        this.exerciseRepository = exerciseRepository;
        this.exerciseGroupService = exerciseGroupService;
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/exercise-groups : Create a new exercise group.
     *
     * @param courseId               the course to which the exercise group belongs to
     * @param examId                 the exam to which the exercise group belongs to
     * @param exerciseGroupCreateDTO the exercise group to create
     * @return the ResponseEntity with status 201 (Created) and with the new exercise group as body,
     *         or with status 400 (Bad Request) if the exerciseGroup has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exercise-groups")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseGroupDTO> createExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody ExerciseGroupCreateDTO exerciseGroupCreateDTO)
            throws URISyntaxException {
        log.debug("REST request to create an exercise group : {}", exerciseGroupCreateDTO);
        if (exerciseGroupCreateDTO.id() != null) {
            throw new BadRequestAlertException("A new exerciseGroup cannot already have an ID", ENTITY_NAME, "idExists");
        }

        if (exerciseGroupCreateDTO.exam() == null) {
            throw new ConflictException("The exercise group has to belong to an exam.", ENTITY_NAME, "missingExam");
        }

        if (!examId.equals(exerciseGroupCreateDTO.exam().id())) {
            throw new ConflictException("The exam connected to this group does not have the given exam id.", ENTITY_NAME, "wrongExamId");
        }

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        // The persisted entity is built from the DTO's title / mandatory flag only; the target exam comes from the path
        // (the DTO's exam reference is validated above, never persisted).
        ExerciseGroup exerciseGroup = exerciseGroupCreateDTO.toEntity();

        // Save the exerciseGroup as part of the exam to ensure that the order column is set correctly
        Exam examFromDB = examRepository.findByIdWithExerciseGroupsElseThrow(examId);
        examFromDB.addExerciseGroup(exerciseGroup);
        Exam savedExam = examRepository.save(examFromDB);
        ExerciseGroup savedExerciseGroup = savedExam.getExerciseGroups().getLast();

        return ResponseEntity.created(new URI("/api/exam/courses/" + courseId + "/exams/" + examId + "/exercise-groups/" + savedExerciseGroup.getId()))
                .body(ExerciseGroupDTO.of(savedExerciseGroup));
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/exercise-groups : Update an existing exercise group.
     *
     * @param courseId               the course to which the exercise group belongs to
     * @param examId                 the exam to which the exercise group belongs to
     * @param exerciseGroupUpdateDTO the exercise group update DTO containing the new values
     * @return the ResponseEntity with status 200 (OK) and with the body of the updated exercise group
     */
    @PutMapping("courses/{courseId}/exams/{examId}/exercise-groups")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseGroupDTO> updateExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestBody ExerciseGroupUpdateDTO exerciseGroupUpdateDTO) {
        log.debug("REST request to update an exercise group : {}", exerciseGroupUpdateDTO);

        if (exerciseGroupUpdateDTO.id() == null) {
            throw new BadRequestAlertException("An exercise group update must have an ID", ENTITY_NAME, "idMissing");
        }

        // Fetch the existing exercise group from the database (this is the managed entity)
        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdElseThrow(exerciseGroupUpdateDTO.id());

        // Check access using the managed entity
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, courseId, examId, exerciseGroup);

        // Apply DTO values to the managed entity
        exerciseGroupUpdateDTO.applyTo(exerciseGroup);

        ExerciseGroup result = exerciseGroupRepository.save(exerciseGroup);
        return ResponseEntity.ok(ExerciseGroupDTO.of(result));
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/exercises/{exerciseId}/exercise-group : Move an exam exercise into a
     * different exercise group of the same exam.
     * <p>
     * Blocked once a student exam exists: generation has already picked one exercise per group, so a later move would
     * desync those selections and the exam's point totals.
     *
     * @param courseId      the course to which the exam belongs to
     * @param examId        the exam to which the exercise and both exercise groups belong to
     * @param exerciseId    the id of the exercise to move
     * @param assignmentDTO the target exercise group
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/exams/{examId}/exercises/{exerciseId}/exercise-group")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> moveExerciseToGroup(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long exerciseId,
            @Valid @RequestBody ExamExerciseGroupAssignmentDTO assignmentDTO) {
        log.debug("REST request to move exercise {} in exam {} to exercise group {}", exerciseId, examId, assignmentDTO.exerciseGroupId());

        ExerciseGroup targetGroup = exerciseGroupRepository.findByIdElseThrow(assignmentDTO.exerciseGroupId());
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, courseId, examId, targetGroup);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.getExam() == null || !examId.equals(exercise.getExam().getId())) {
            throw new BadRequestAlertException("The exercise does not belong to this exam", ENTITY_NAME, "examIdMismatch");
        }
        exerciseGroupService.moveExerciseToGroup(examId, exerciseId, targetGroup.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/import-exercise-group : Imports exercise groups to the specified exam
     *
     * @param courseId             the course to which the exam belongs
     * @param examId               the exam to which the exercise groups should be added
     * @param updatedExerciseGroup the list of Exercise Groups to be imported
     * @param importId             an optional client-supplied id; when present, live import progress is sent to the importing user over a websocket
     * @return the ResponseEntity with status 201 (Created) and with body the newly imported exercise groups, or with status 400 (Bad Request)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/import-exercise-group")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseGroupImportResultDTO> importExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestBody List<ExerciseGroup> updatedExerciseGroup, @RequestParam(required = false) String importId) throws IOException {
        log.debug("REST request to import {} exercise group(s) to exam {}", updatedExerciseGroup.size(), examId);

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        // When the client supplies an importId, live progress is reported to the importing user over a websocket so the UI
        // can show a progress dialog while this (synchronous) request runs.
        ExerciseGroupImportResultDTO importResult = examImportService.importExerciseGroupsWithExercisesToExistingExam(updatedExerciseGroup, examId, courseId, importId,
                userRepository.getCurrentUserLogin());

        // The exercise groups are always created. Any exercises that could not be imported are reported in the response
        // body, split into "skipped" (cleanly not imported) and "incomplete" (failed partway, may need review), so the
        // editor gets precise feedback instead of the whole import failing.
        return ResponseEntity.ok(importResult);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/exercise-groups/{exerciseGroupId} : Find an exercise group by id.
     *
     * @param courseId        the course to which the exercise group belongs to
     * @param examId          the exam to which the exercise group belongs to
     * @param exerciseGroupId the id of the exercise group to find
     * @return the ResponseEntity with status 200 (OK) and with the found exercise group as body
     */
    @GetMapping("courses/{courseId}/exams/{examId}/exercise-groups/{exerciseGroupId}")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseGroupDTO> getExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long exerciseGroupId) {
        log.debug("REST request to get exercise group : {}", exerciseGroupId);

        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdElseThrow(exerciseGroupId);
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, courseId, examId, exerciseGroup);

        return ResponseEntity.ok(ExerciseGroupDTO.of(exerciseGroup));
    }

    /**
     * GET courses/{courseId}/exams/{examId}/exercise-groups : Get all exercise groups of the given exam
     *
     * @param courseId the course to which the exercise groups belong to
     * @param examId   the exam to which the exercise groups belong to
     * @return the ResponseEntity with status 200 (OK) and a list of exercise groups. The list can be empty
     */
    @GetMapping("courses/{courseId}/exams/{examId}/exercise-groups")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ExerciseGroupDTO>> getExerciseGroupsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all exercise groups for exam : {}", examId);

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        List<ExerciseGroup> exerciseGroupList = exerciseGroupRepository.findWithExamAndExercisesByExamId(examId);
        List<ExerciseGroupDTO> exerciseGroupDTOs = exerciseGroupList.stream().map(ExerciseGroupDTO::ofWithExercises).toList();
        return ResponseEntity.ok(exerciseGroupDTOs);
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/exercise-groups/{exerciseGroupId} : Delete the exercise group with the given id.
     *
     * @param courseId                  the course to which the exercise group belongs to
     * @param examId                    the exam to which the exercise group belongs to
     * @param exerciseGroupId           the id of the exercise group to delete
     * @param deleteBaseReposBuildPlans boolean which states whether the base repos and build plans should be deleted as well, this is true by default because for LocalVC and
     *                                      LocalCI, it does not make sense to keep these artifacts
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/exercise-groups/{exerciseGroupId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteExerciseGroup(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long exerciseGroupId,
            @RequestParam(defaultValue = "true") boolean deleteBaseReposBuildPlans) {
        log.info("REST request to delete exercise group : {}", exerciseGroupId);

        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdWithExercisesElseThrow(exerciseGroupId);
        examAccessService.checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, courseId, examId, exerciseGroup);

        User user = userRepository.getUser();
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXERCISE_GROUP, "exerciseGroup=" + exerciseGroup.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete the exercise group {}", user.getLogin(), exerciseGroup.getTitle());

        for (Exercise exercise : exerciseGroup.getExercises()) {
            exerciseDeletionService.delete(exercise.getId(), deleteBaseReposBuildPlans);
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
