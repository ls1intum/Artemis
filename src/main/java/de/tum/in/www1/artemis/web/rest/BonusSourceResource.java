package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.BonusSource;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.BonusSourceRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.BonusSourceService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing bonus source
 */
@RestController
@RequestMapping("/api")
public class BonusSourceResource {

    private final Logger log = LoggerFactory.getLogger(BonusSourceResource.class);

    private static final String ENTITY_NAME = "bonusSource";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final BonusSourceService bonusSourceService;

    private final BonusSourceRepository bonusSourceRepository;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final AuthorizationCheckService authCheckService;

    public BonusSourceResource(BonusSourceService bonusSourceService, BonusSourceRepository bonusSourceRepository, CourseRepository courseRepository, ExamRepository examRepository,
            GradingScaleRepository gradingScaleRepository, AuthorizationCheckService authCheckService) {
        this.bonusSourceService = bonusSourceService;
        this.bonusSourceRepository = bonusSourceRepository;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * TODO: Ata
     * GET /courses/{courseId}/bonus-sources : Find bonus source for course
     *
     * @param courseId the course to which the bonus source belongs
     * @return ResponseEntity with status 200 (Ok) with body the bonus source if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/bonus-sources")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BonusSource> getBonusSourcesWithSourceCourse(@PathVariable Long courseId) {
        log.debug("REST request to get bonus source for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<BonusSource> bonusSource = bonusSourceRepository.findBySourceCourseId(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return bonusSource.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * TODO: Ata
     * GET /courses/{courseId}/exams/{examId}/bonus-sources : Find bonus source for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam to which the bonus source belongs
     * @return ResponseEntity with status 200 (Ok) with body the bonus source if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/bonus-sources")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BonusSource> getBonusSourcesWithSourceExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get bonus sources for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<BonusSource> bonusSource = bonusSourceRepository.findBySourceExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return bonusSource.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * GET bonus-sources/:bonusSourceId : get the bonusSource with id.
     *
     * @param bonusSourceId the id of the bonusSource to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the bonusSource, or with status 404 (Not Found)
     */
    @GetMapping("bonus-sources/{bonusSourceId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BonusSource> getBonusSource(@PathVariable Long bonusSourceId) {
        log.debug("REST request to get BonusSource : {}", bonusSourceId);
        BonusSource bonusSource = bonusSourceRepository.findById(bonusSourceId).orElseThrow();
        checkIsAtLeastInstructorForGradingScaleCourse(bonusSource.getTargetGradingScale());

        return ResponseEntity.ok().body(bonusSource);
    }

    // /**
    // * Search for all modeling exercises by title and course title. The result is pageable since there might be hundreds
    // * of exercises in the DB.
    // *
    // * @param search The pageable search containing the page size, page number and query string
    // * @return The desired page, sorted and matching the given query
    // */
    // @GetMapping("modeling-exercises")
    // @PreAuthorize("hasRole('EDITOR')")
    // public ResponseEntity<SearchResultPageDTO<ModelingExercise>> getAllExercisesOnPage(PageableSearchDTO<String> search) {
    // final var user = userRepository.getUserWithGroupsAndAuthorities();
    // return ResponseEntity.ok(modelingExerciseService.getAllOnPageWithSize(search, user));
    // }

    // /**
    // * POST /courses/{courseId}/bonus-sources : Create bonus source for course
    // *
    // * @param courseId the course to which the bonus source belongs
    // * @param bonusSource the bonus source which will be created
    // * @return ResponseEntity with status 201 (Created) with body the new bonus source if no such exists for the course
    // * and if it is correctly formatted and 400 (Bad request) otherwise
    // */
    // @PostMapping("/courses/{courseId}/bonus-sources")
    // @PreAuthorize("hasRole('INSTRUCTOR')")
    // public ResponseEntity<BonusSource> createBonusSourceForCourse(@PathVariable Long courseId, @RequestBody BonusSource bonusSource) throws URISyntaxException {
    // log.debug("REST request to create a bonus source for course: {}", courseId);
    // Course course = courseRepository.findByIdElseThrow(courseId);
    // Optional<BonusSource> existingBonusSource = bonusSourceRepository.findByCourseId(courseId);
    // authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    // validateBonusSource(existingBonusSource, bonusSource);
    //
    // if (!Objects.equals(bonusSource.getCourse().getMaxPoints(), course.getMaxPoints())) {
    // course.setMaxPoints(bonusSource.getCourse().getMaxPoints());
    // courseRepository.save(course);
    // }
    // bonusSource.setCourse(course);
    //
    // BonusSource savedBonusSource = bonusSourceService.saveBonusSource(bonusSource);
    // return ResponseEntity.created(new URI("/api/courses/" + courseId + "/bonus-sources/")).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ""))
    // .body(savedBonusSource);
    // }

    // private void validateBonusSource(Optional<BonusSource> existingBonusSource, BonusSource bonusSource) {
    // if (existingBonusSource.isPresent()) {
    // throw new BadRequestAlertException("A bonus source already exists", ENTITY_NAME, "bonusSourceAlreadyExists");
    // }
    // else if (bonusSource.getGradeSteps() == null || bonusSource.getGradeSteps().isEmpty()) {
    // throw new BadRequestAlertException("A bonus source must contain grade steps", ENTITY_NAME, "emptyGradeSteps");
    // }
    // else if (bonusSource.getId() != null) {
    // throw new BadRequestAlertException("A bonus source can't contain a predefined id", ENTITY_NAME, "bonusSourceHasId");
    // }
    // }

    /**
     * POST /courses/{courseId}/exams/{examId}/bonus-sources : Create bonus source for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam to which the bonus source belongs
     * @param bonusSource the bonus source which will be created
     * @return ResponseEntity with status 201 (Created) with body the new bonus source if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/bonus-sources")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BonusSource> createBonusSourceForTargetExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody BonusSource bonusSource)
            throws URISyntaxException {
        log.debug("REST request to create a bonus source for target exam: {}", examId);
        if (bonusSource.getId() != null) {
            throw new BadRequestAlertException("A new modeling exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }

        Course course = courseRepository.findByIdElseThrow(courseId);
        // Optional<BonusSource> existingBonusSource = bonusSourceRepository.findBySourceExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        // TODO: Ata: Check source course too (see update method)

        // validateBonusSource(existingBonusSource, bonusSource);
        GradingScale examGradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);

        bonusSource.setTargetGradingScale(examGradingScale);

        BonusSource savedBonusSource = bonusSourceService.saveBonusSource(bonusSource);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examId + "/bonus-sources/" + savedBonusSource.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonusSource);
    }

    /**
     * PUT /courses/{courseId}/bonus-sources : Update bonus source for course
     *
     * @param bonusSource the bonus source which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated bonus source if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("/bonus-sources")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BonusSource> updateBonusSource(@RequestBody BonusSource bonusSource) {
        log.debug("REST request to update a bonus source: {}", bonusSource.getId());
        // Course course = courseRepository.findByIdElseThrow(courseId);
        BonusSource oldBonusSource = bonusSourceRepository.findById(bonusSource.getId()).orElseThrow();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, oldBonusSource.getTargetGradingScale().getExam().getCourse(), null);
        bonusSource.setTargetGradingScale(oldBonusSource.getTargetGradingScale());

        GradingScale sourceGradingScale = oldBonusSource.getSourceGradingScale();
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScale);
        // bonusSource.setId(oldBonusSource.getId());
        // if (!Objects.equals(bonusSource.getCourse().getMaxPoints(), course.getMaxPoints())) {
        // course.setMaxPoints(bonusSource.getCourse().getMaxPoints());
        // courseRepository.save(course);
        // }
        // bonusSource.setCourse(course);
        BonusSource savedBonusSource = bonusSourceService.saveBonusSource(bonusSource);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonusSource);
    }

    private void checkIsAtLeastInstructorForGradingScaleCourse(GradingScale sourceGradingScale) {
        Course sourceCourse = sourceGradingScale.getExam() != null ? sourceGradingScale.getExam().getCourse() : sourceGradingScale.getCourse();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sourceCourse, null);
    }

    // /**
    // * PUT /courses/{courseId}/exams/{examId}/bonus-sources : Update bonus source for exam
    // *
    // * @param courseId the course to which the exam belongs
    // * @param examId the exam to which the bonus source belongs
    // * @param bonusSource the bonus source which will be updated
    // * @return ResponseEntity with status 200 (Ok) with body the newly updated bonus source if it is correctly formatted and 400 (Bad request) otherwise
    // */
    // @PutMapping("/courses/{courseId}/exams/{examId}/bonus-sources")
    // @PreAuthorize("hasRole('INSTRUCTOR')")
    // public ResponseEntity<BonusSource> updateBonusSourceForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody BonusSource bonusSource) {
    // log.debug("REST request to update a bonus source for exam: {}", examId);
    // Course course = courseRepository.findByIdElseThrow(courseId);
    // Exam exam = examRepository.findByIdElseThrow(examId);
    // BonusSource oldBonusSource = bonusSourceRepository.findByExamIdOrElseThrow(examId);
    // authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    // bonusSource.setId(oldBonusSource.getId());
    // if (bonusSource.getExam().getMaxPoints() != exam.getMaxPoints()) {
    // exam.setMaxPoints(bonusSource.getExam().getMaxPoints());
    // examRepository.save(exam);
    // }
    // bonusSource.setExam(exam);
    // BonusSource savedBonusSource = bonusSourceService.saveBonusSource(bonusSource);
    // return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonusSource);
    // }

    /**
     * DELETE /courses/{courseId}/bonus-sources : Delete bonus source for course
     *
     * @param bonusSourceId the id of the bonus source to delete
     * @return ResponseEntity with status 200 (Ok) if the bonus source is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("/bonus-sources/{bonusSourceId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteBonusSourceForCourse(@PathVariable Long bonusSourceId) {
        log.debug("REST request to delete the bonus source: {}", bonusSourceId);
        BonusSource bonusSource = bonusSourceRepository.findById(bonusSourceId).orElseThrow();
        checkIsAtLeastInstructorForGradingScaleCourse(bonusSource.getTargetGradingScale());
        bonusSourceRepository.delete(bonusSource);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    // /**
    // * DELETE /courses/{courseId}/exams/{examId}/bonus-sources : Delete bonus source for course
    // *
    // * @param courseId the course to which the exam belongs
    // * @param examId the exam to which the bonus source belongs
    // * @return ResponseEntity with status 200 (Ok) if the bonus source is successfully deleted and 400 (Bad request) otherwise
    // */
    // @DeleteMapping("/courses/{courseId}/exams/{examId}/bonus-sources")
    // @PreAuthorize("hasRole('INSTRUCTOR')")
    // public ResponseEntity<Void> deleteBonusSourceForExam(@PathVariable Long courseId, @PathVariable Long examId) {
    // log.debug("REST request to delete the bonus source for exam: {}", examId);
    // Course course = courseRepository.findByIdElseThrow(courseId);
    // BonusSource bonusSource = bonusSourceRepository.findByExamIdOrElseThrow(examId);
    // authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    // bonusSourceRepository.delete(bonusSource);
    // return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    // }

}
