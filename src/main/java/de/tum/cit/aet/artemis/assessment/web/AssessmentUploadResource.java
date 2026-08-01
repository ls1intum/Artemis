package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadResultDTO;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller that lets instructors upload manual assessments for the participants of a programming exercise at once.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
public class AssessmentUploadResource {

    private static final Logger log = LoggerFactory.getLogger(AssessmentUploadResource.class);

    private static final String ENTITY_NAME = "assessmentUpload";

    /** Invariant: never {@code null} after construction (constructor-injected and {@code final}). */
    private final AssessmentUploadService assessmentUploadService;

    /** Invariant: never {@code null} after construction (constructor-injected and {@code final}). */
    private final ProgrammingExerciseRepository programmingExerciseRepository;

    /** Invariant: never {@code null} after construction (constructor-injected and {@code final}). */
    private final MultipartProperties multipartProperties;

    /**
     * Creates a resource for importing manual assessments.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}.
     *
     * @param assessmentUploadService       the service that validates and stores uploaded assessments
     * @param programmingExerciseRepository the repository used to resolve the target programming exercise
     * @param multipartProperties           the configured multipart upload limits
     * @throws IllegalArgumentException if any parameter is {@code null}
     */
    public AssessmentUploadResource(final AssessmentUploadService assessmentUploadService, final ProgrammingExerciseRepository programmingExerciseRepository,
            final MultipartProperties multipartProperties) {
        if (Stream.of(assessmentUploadService, programmingExerciseRepository, multipartProperties).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The assessment upload resource dependencies must not be null");
        }
        this.assessmentUploadService = assessmentUploadService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.multipartProperties = multipartProperties;
    }

    /**
     * POST exercises/{exerciseId}/manual-assessments : Upload a zip file containing manual assessments for the participants of a programming exercise.
     * <p>
     * The zip file has to contain exactly one {@code assessment-scores.csv} file (identifying each participant by the repository-export identifier
     * {@code <participationId>-<login>}
     * in
     * its first column and providing the points in an {@code Overall points} column) and one {@code .txt} file per participant (named after the exported repository folder, its
     * content
     * becomes the manual feedback). The whole file is validated before anything is stored (all-or-nothing).
     * <p>
     * <b>Preconditions:</b> the caller is at least instructor in the exercise's course (enforced by {@link EnforceAtLeastInstructorInExercise}); {@code exerciseId} refers to an
     * existing programming exercise; {@code zipFile} is present, non-empty and does not exceed the configured maximum upload size.
     * <p>
     * <b>Postconditions:</b> the persistent state is only changed when the returned body has no errors, in which case a manual assessment was created or overwritten for every CSV
     * row (all-or-nothing).
     *
     * @param exerciseId the id of the programming exercise the assessments belong to
     * @param zipFile    the uploaded zip file
     * @return {@code 200 (OK)} with a {@link AssessmentUploadResultDTO} describing the created assessments on success, or the collected validation errors if the content
     *         was rejected (nothing is stored in that case)
     * @throws BadRequestAlertException if the uploaded file is empty or exceeds the configured maximum upload size
     */
    @PostMapping(value = "exercises/{exerciseId}/manual-assessments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<AssessmentUploadResultDTO> uploadManualAssessments(@PathVariable final long exerciseId, @RequestParam("file") final MultipartFile zipFile) {
        log.debug("REST request to upload manual assessments for programming exercise {} from file {}", exerciseId, zipFile.getOriginalFilename());
        if (zipFile.isEmpty()) {
            throw new BadRequestAlertException("The uploaded file is empty", ENTITY_NAME, "assessmentUpload.fileEmpty");
        }

        final DataSize maxSize = multipartProperties.getMaxFileSize();
        final long maxBytes = maxSize.toBytes();
        if (maxBytes > 0 && zipFile.getSize() > maxBytes) {
            throw new BadRequestAlertException("The uploaded file exceeds the %s limit".formatted(maxSize.toString()), ENTITY_NAME, "assessmentUpload.fileTooLarge",
                    Map.of("maxSize", maxSize.toString()));
        }

        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        final AssessmentUploadResultDTO result = assessmentUploadService.importAssessments(exercise, zipFile);
        return ResponseEntity.ok(result);
    }
}
