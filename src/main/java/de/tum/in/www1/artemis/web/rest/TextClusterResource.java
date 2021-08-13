package de.tum.in.www1.artemis.web.rest;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.TextCluster;
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
        var clusterStats = textClusterRepository.getClusterStatistics(exerciseId);
        log.debug("REST request to get clusterStats: {}", clusterStats);
        return ResponseEntity.ok().body(clusterStats);
    }

    /**
     * Put /clusters/{clusterId}/disable
     * <p>
     * Sets cluster disabled boolean value
     *
     * @return The status whether the boolean value was set successfully or the setting failed.
     */
    @PutMapping("/clusters/{clusterId}/disable")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> setClusterDisabledPredicate(@PathVariable Long clusterId, @RequestParam(value = "disabled") boolean disabled) {
        log.info("REST request to disable Cluster : {}", clusterId);
        final Optional<TextCluster> cluster = textClusterRepository.findById(clusterId);
        if (cluster.isPresent()) {
            cluster.get().setDisabled(disabled);
            textClusterRepository.save(cluster.get());
        }
        else {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}
