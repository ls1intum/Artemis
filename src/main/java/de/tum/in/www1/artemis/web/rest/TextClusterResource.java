package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.repository.TextClusterRepository;

/**
 * REST controller for managing TextClusterResource.
 */
@RestController
@RequestMapping("/api")
public class TextClusterResource {

    private final Logger log = LoggerFactory.getLogger(TextClusterResource.class);

    private final TextClusterRepository textClusterRepository;

    public TextClusterResource(TextClusterRepository textClusterRepository) {
        this.textClusterRepository = textClusterRepository;
    }

    /**
     * Get /cluster-stats
     * <p>
     * Get text cluster stats
     *
     * @return The list of cluster ids adjacent to their respective sizes and automatically graded textblocks
     */
    @GetMapping("/cluster-stats/{exerciseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TextClusterRepository.TextClusterStats>> getClusterStats(@PathVariable Long exerciseId) {
        // TODO: check that the editor has access to the corresponding course (add the exerciseId to the URL)

        var clusterStats = textClusterRepository.findCountOfAutoFeedbacks(exerciseId);
        log.debug("REST request to get clusterStats: {}", clusterStats);
        return ResponseEntity.ok().body(clusterStats);
    }
}
