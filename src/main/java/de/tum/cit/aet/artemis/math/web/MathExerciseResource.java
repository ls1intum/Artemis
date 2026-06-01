package de.tum.cit.aet.artemis.math.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.dto.MathExerciseDTO;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.service.MathExerciseImportService;

@Lazy
@Conditional(MathEnabled.class)
@RestController
@RequestMapping("api/math/")
public class MathExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(MathExerciseResource.class);

    private static final String ENTITY_NAME = "mathExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MathExerciseRepository mathExerciseRepository;

    private final MathExerciseImportService mathExerciseImportService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final AuthorizationCheckService authCheckService;

    public MathExerciseResource(MathExerciseRepository mathExerciseRepository, MathExerciseImportService mathExerciseImportService, CourseRepository courseRepository,
            UserRepository userRepository, ExerciseSpecificationService exerciseSpecificationService, AuthorizationCheckService authCheckService) {
        this.mathExerciseRepository = mathExerciseRepository;
        this.mathExerciseImportService = mathExerciseImportService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /math-exercises : create a new math exercise.
     *
     * @param mathExerciseDTO the exercise to create
     * @return the created exercise
     * @throws URISyntaxException if the location URI cannot be constructed
     */
    @PostMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExerciseDTO> createMathExercise(@RequestBody MathExerciseDTO mathExerciseDTO) throws URISyntaxException {
        log.debug("REST request to create MathExercise : {}", mathExerciseDTO);
        if (mathExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new math exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        MathExercise exercise = new MathExercise();
        mathExerciseDTO.applyToEntity(exercise);
        applyCourse(mathExerciseDTO, exercise);
        MathExercise saved = mathExerciseRepository.findByIdWithCategories(mathExerciseRepository.save(exercise).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/math/math-exercises/" + saved.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, saved.getTitle())).body(MathExerciseDTO.of(saved));
    }

    /**
     * PUT /math-exercises : update an existing math exercise. Delegates to create if the
     * DTO has no id, matching the behaviour of the other exercise resources.
     *
     * @param mathExerciseDTO the exercise to update
     * @return the updated exercise
     * @throws URISyntaxException if the location URI cannot be constructed
     */
    @PutMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExerciseDTO> updateMathExercise(@RequestBody MathExerciseDTO mathExerciseDTO) throws URISyntaxException {
        log.debug("REST request to update MathExercise : {}", mathExerciseDTO);
        if (mathExerciseDTO.id() == null) {
            return createMathExercise(mathExerciseDTO);
        }
        MathExercise existing = mathExerciseRepository.findByIdWithCategoriesAndCourse(mathExerciseDTO.id()).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, existing, null);
        mathExerciseDTO.applyToEntity(existing);
        applyCourse(mathExerciseDTO, existing);
        MathExercise saved = mathExerciseRepository.findByIdWithCategories(mathExerciseRepository.save(existing).getId()).orElseThrow();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, saved.getId().toString())).body(MathExerciseDTO.of(saved));
    }

    /**
     * GET /courses/{courseId}/math-exercises : list the math exercises of a course.
     *
     * @param courseId the id of the course
     * @return the math exercises of the course
     */
    @GetMapping("courses/{courseId}/math-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<MathExerciseDTO>> getMathExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all MathExercises for course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<MathExerciseDTO> exercises = mathExerciseRepository.findByCourseIdWithCategories(courseId).stream().map(MathExerciseDTO::of).toList();
        return ResponseEntity.ok(exercises);
    }

    /**
     * GET /math-exercises/{exerciseId} : get the math exercise with the given id.
     *
     * @param exerciseId the id of the exercise to retrieve
     * @return the exercise
     */
    @GetMapping("math-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<MathExerciseDTO> getMathExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get MathExercise : {}", exerciseId);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourse(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        return ResponseEntity.ok(MathExerciseDTO.of(exercise));
    }

    /**
     * DELETE /math-exercises/{exerciseId} : delete the math exercise with the given id.
     *
     * @param exerciseId the id of the exercise to delete
     * @return an empty response
     */
    @DeleteMapping("math-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteMathExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to delete MathExercise : {}", exerciseId);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourse(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        mathExerciseRepository.deleteById(exerciseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).build();
    }

    /**
     * GET /math-exercises : get all the math exercises (course-only; math exercises cannot live in exams).
     *
     * @param search the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of math exercises in body
     */
    @GetMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<MathExerciseDTO>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search) {
        log.debug("REST request to get all MathExercises on page");
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Specification<MathExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(search.getSearchTerm(), true, false, user, pageable);
        Page<MathExercise> exercisePage = mathExerciseRepository.findAll(specification, pageable);
        List<MathExerciseDTO> dtos = exercisePage.getContent().stream().map(MathExerciseDTO::of).toList();
        return ResponseEntity.ok(new SearchResultPageDTO<>(dtos, exercisePage.getTotalPages()));
    }

    /**
     * POST /math-exercises/import/:sourceExerciseId : import a math exercise from an existing one
     *
     * @param sourceExerciseId    the id of the math exercise to import
     * @param importedExerciseDTO the math exercise to import
     * @return the ResponseEntity with status 201 (Created) and with body the new math exercise, or with status 400 (Bad Request) if the math exercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("math-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExerciseDTO> importExercise(@PathVariable Long sourceExerciseId, @RequestBody MathExerciseDTO importedExerciseDTO) throws URISyntaxException {
        log.debug("REST request to import MathExercise from {} : {}", sourceExerciseId, importedExerciseDTO);
        if (importedExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new math exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        MathExercise sourceExercise = mathExerciseRepository.findByIdWithCategoriesAndCourse(sourceExerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, sourceExercise, null);
        MathExercise target = new MathExercise();
        importedExerciseDTO.applyToEntity(target);
        applyCourse(importedExerciseDTO, target);
        MathExercise result = mathExerciseRepository.findByIdWithCategories(mathExerciseImportService.importMathExercise(sourceExercise, target).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/math/math-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(MathExerciseDTO.of(result));
    }

    private void applyCourse(MathExerciseDTO dto, MathExercise exercise) {
        if (dto.courseId() == null) {
            throw new BadRequestAlertException("A math exercise must belong to a course", ENTITY_NAME, "courseRequired");
        }
        Course course = courseRepository.findByIdElseThrow(dto.courseId());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        exercise.setCourse(course);
        exercise.setExerciseGroup(null);
    }
}
