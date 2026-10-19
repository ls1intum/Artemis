package de.tum.cit.aet.artemis.text.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.service.GradingCriteriaMapper;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExerciseGroupApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.ImportTextExerciseDTO;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseImportService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

/**
 * REST controller for managing TextExercise.
 */
@Conditional(TextEnabled.class)
@Lazy
@FeatureUsage(UserFeature.TEXT_AUTHORING)
@RestController
@RequestMapping("api/text/")
public class TextExerciseExportImportResource {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseExportImportResource.class);

    private static final String ENTITY_NAME = "textExercise";

    private final TextExerciseImportService textExerciseImportService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final AuthorizationCheckService authCheckService;

    private final TextExerciseRepository textExerciseRepository;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final CourseRepository courseRepository;

    private final Optional<ExerciseGroupApi> exerciseGroupApi;

    public TextExerciseExportImportResource(TextExerciseRepository textExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            TextExerciseImportService textExerciseImportService, TextSubmissionExportService textSubmissionExportService, ExerciseVersionService exerciseVersionService,
            CourseRepository courseRepository, Optional<ExerciseGroupApi> exerciseGroupApi) {
        this.textExerciseRepository = textExerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.textExerciseImportService = textExerciseImportService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.exerciseVersionService = exerciseVersionService;
        this.courseRepository = courseRepository;
        this.exerciseGroupApi = exerciseGroupApi;
    }

    /**
     * POST /text-exercises/import: Imports an existing text exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates. Referenced
     * entities will get cloned and assigned a new id.
     *
     * @param sourceExerciseId  The ID of the original exercise which should get imported
     * @param importExerciseDTO The new exercise containing values that should get overwritten in the
     *                              imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist,
     *         or a forbidden error (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("text-exercises/import")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExerciseResponseDTO> importExercise(@RequestParam long sourceExerciseId, @RequestBody ImportTextExerciseDTO importExerciseDTO)
            throws URISyntaxException {
        // Build a transient entity from the dumb DTO, attaching managed Course/ExerciseGroup loaded by id.
        final TextExercise importedExercise = toExercise(importExerciseDTO);
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        final var user = userRepository.getUserWithAuthorities();
        final var originalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalTextExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();

        final var newTextExercise = textExerciseImportService.importTextExercise(importedExercise, originalTextExercise);
        exerciseVersionService.createExerciseVersion(newTextExercise, user);
        return ResponseEntity.created(new URI("/api/text/text-exercises/" + newTextExercise.getId())).body(TextExerciseResponseDTO.of(newTextExercise));
    }

    /**
     * Builds a transient {@link TextExercise} from the import request DTO.
     * <p>
     * Only scalar/enum/date fields and nested config are set. The Course / ExerciseGroup referenced by id are loaded as
     * managed entities (so the subsequent role checks have access to the configured groups) and attached to the new
     * transient exercise. No entity graph from the request is persisted.
     *
     * @param dto the import payload
     * @return a transient TextExercise carrying the values to overwrite in the imported exercise
     */
    private TextExercise toExercise(ImportTextExerciseDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("No text exercise was provided.", ENTITY_NAME, "isNull");
        }
        TextExercise exercise = new TextExercise();
        dto.applyImportSettingsTo(exercise);
        exercise.setExampleSolution(dto.exampleSolution());

        if (dto.teamAssignmentConfig() != null) {
            exercise.setTeamAssignmentConfig(dto.teamAssignmentConfig().toEntity());
        }
        if (dto.plagiarismDetectionConfig() != null) {
            exercise.setPlagiarismDetectionConfig(dto.plagiarismDetectionConfig().toEntity());
        }

        // Grading criteria (with their structured grading instructions, needed for the copy tracker during import)
        if (dto.gradingCriteria() != null && !dto.gradingCriteria().isEmpty()) {
            exercise.setGradingCriteria(GradingCriteriaMapper.create(dto.gradingCriteria(), exercise));
        }

        // Competency links as new unmanaged objects referencing only the competency id
        if (dto.competencyLinks() != null && !dto.competencyLinks().isEmpty()) {
            exercise.setCompetencyLinks(CompetencyExerciseLinkService.toTransientLinks(dto, exercise, ENTITY_NAME));
        }

        // Attach a managed Course and/or ExerciseGroup so role checks see the configured groups. The exclusivity invariant
        // (exactly one of the two) is validated downstream by checkCourseAndExerciseGroupExclusivity.
        if (dto.courseId() != null) {
            Course course = courseRepository.findByIdElseThrow(dto.courseId());
            exercise.setCourse(course);
        }
        if (dto.exerciseGroupId() != null) {
            ExerciseGroup exerciseGroup = exerciseGroupApi.orElseThrow(() -> new ExamApiNotPresentException(ExerciseGroupApi.class)).findByIdElseThrow(dto.exerciseGroupId());
            exercise.setExerciseGroup(exerciseGroup);
        }
        return exercise;
    }

    /**
     * POST /text-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("text-exercises/{exerciseId}/export-submissions")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, textExercise, null);

        Path zipFilePath = textSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFilePath);
    }
}
