package de.tum.in.www1.artemis.web.rest;

import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.repository.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;

/**
 * REST controller for managing TextExercise.
 */
@RestController
@RequestMapping("/api")
public class PlagiarismResource {

    private final Logger log = LoggerFactory.getLogger(PlagiarismResource.class);

    private final PlagiarismService plagiarismService;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    public PlagiarismResource(PlagiarismService plagiarismService, PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.plagiarismService = plagiarismService;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

    /**
     * PUT /plagiarism-comparisons/{comparisonId}/status
     * <p>
     * Update the status of the plagiarism comparison with the given ID.
     *
     * @param comparisonId ID of the plagiarism comparison to update the status of
     * @param statusDTO new status for the given comparison
     * @return The ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the
     * parameters are invalid
     */
    @PutMapping("/plagiarism-comparisons/{comparisonId}/status")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> updatePlagiarismComparisonStatus(@PathVariable long comparisonId, @RequestBody PlagiarismComparisonStatusDTO statusDTO) {
        // TODO: check that the instructor has access to the corresponding course (add the exerciseId to the URL)
        log.debug("REST request to update the status of the plagiarism comparison with id: {}", comparisonId);
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(comparisonId);
        plagiarismService.updateStatusOfComparison(comparison, statusDTO.getStatus());
        return ResponseEntity.ok().body(null);
    }
}
