package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
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

    public PlagiarismResource(PlagiarismService plagiarismService) {
        this.plagiarismService = plagiarismService;
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
        log.debug("REST request to update the status of the plagiarism comparison with id: {}", comparisonId);

        Optional<PlagiarismComparison> optionalComparison = plagiarismService.getPlagiarismComparison(comparisonId);

        if (optionalComparison.isEmpty()) {
            return notFound();
        }

        PlagiarismComparison comparison = optionalComparison.get();

        plagiarismService.updateStatusOfComparison(comparison, statusDTO.status);

        return ResponseEntity.ok().body(null);
    }
}
