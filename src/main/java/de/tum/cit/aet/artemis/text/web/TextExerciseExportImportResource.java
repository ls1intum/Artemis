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
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.athena.domain.AthenaModuleMode;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseImportService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

/**
 * REST controller for managing TextExercise.
 */
@Conditional(TextEnabled.class)
@Lazy
@RestController
@RequestMapping("api/text/")
public class TextExerciseExportImportResource {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseExportImportResource.class);

    private static final String ENTITY_NAME = "textExercise";

    private final TextExerciseImportService textExerciseImportService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<AthenaApi> athenaApi;

    private final TextExerciseRepository textExerciseRepository;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    public TextExerciseExportImportResource(TextExerciseRepository textExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            TextExerciseImportService textExerciseImportService, TextSubmissionExportService textSubmissionExportService, Optional<AthenaApi> athenaApi,
            ExerciseVersionService exerciseVersionService) {
        this.textExerciseRepository = textExerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.textExerciseImportService = textExerciseImportService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.athenaApi = athenaApi;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * POST /text-exercises/import: Imports an existing text exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates. Referenced
     * entities will get cloned and assigned a new id.
     *
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param importedExercise The new exercise containing values that should get overwritten in the
     *                             imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist,
     *         or a forbidden error (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("text-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> importExercise(@PathVariable long sourceExerciseId, @RequestBody TextExercise importedExercise) throws URISyntaxException {
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalTextExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();
        // Athena: Check that only allowed athena modules are used, if not we catch the exception and disable feedback suggestions or preliminary feedback for the imported exercise
        // If Athena is disabled and the service is not present, we also disable the corresponding functionality
        if (athenaApi.isPresent()) {
            var api = athenaApi.get();
            try {
                api.checkHasAccessToAthenaModule(importedExercise, importedExercise.getCourseViaExerciseGroupOrCourseMember(), AthenaModuleMode.FEEDBACK_SUGGESTIONS, ENTITY_NAME);
            }
            catch (BadRequestAlertException e) {
                if (importedExercise.getAthenaConfig() != null) {
                    importedExercise.getAthenaConfig().setFeedbackSuggestionModule(null);
                    if (importedExercise.getAthenaConfig().isEmpty()) {
                        importedExercise.setAthenaConfig(null);
                    }
                }
            }
            try {
                api.checkHasAccessToAthenaModule(importedExercise, importedExercise.getCourseViaExerciseGroupOrCourseMember(), AthenaModuleMode.PRELIMINARY_FEEDBACK, ENTITY_NAME);
            }
            catch (BadRequestAlertException e) {
                if (importedExercise.getAthenaConfig() != null) {
                    importedExercise.getAthenaConfig().setPreliminaryFeedbackModule(null);
                    if (importedExercise.getAthenaConfig().isEmpty()) {
                        importedExercise.setAthenaConfig(null);
                    }
                }
            }
        }
        else {
            importedExercise.setAthenaConfig(null);
        }

        final var newTextExercise = textExerciseImportService.importTextExercise(originalTextExercise, importedExercise);
        var savedExercise = textExerciseRepository.save(newTextExercise);
        exerciseVersionService.createExerciseVersion(savedExercise, user);
        return ResponseEntity.created(new URI("/api/text/text-exercises/" + newTextExercise.getId())).body(newTextExercise);
    }

    /**
     * POST /text-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("text-exercises/{exerciseId}/export-submissions")
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, textExercise, null);

        // TAs are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants()) {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, textExercise.getCourseViaExerciseGroupOrCourseMember(), null);
        }

        Path zipFilePath = textSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFilePath);
    }
}
