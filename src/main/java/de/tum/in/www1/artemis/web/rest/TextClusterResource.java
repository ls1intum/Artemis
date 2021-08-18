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
     * Get /text-exercises/{exerciseId}/cluster-statistics/
     * <p>
     * Get text cluster stats
     * @param exerciseId The id of the text exercise to fetch cluster statistics data from
     * @return The list of cluster ids adjacent to their respective sizes and automatically graded text blocks
     */
    @GetMapping("/text-exercises/{exerciseId}/cluster-statistics")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TextClusterRepository.TextClusterStats>> getClusterStats(@PathVariable Long exerciseId) {
        // var clusterStats111 = textClusterRepository.getClusterStatistics2(exerciseId);
        // log.debug("REST request to get clusterStats-11111->: {}", clusterStats111);

        var clusterStats = textClusterRepository.getClusterStatistics(exerciseId);
        log.debug("REST request to get clusterStats-: {}", clusterStats);
        return ResponseEntity.ok().body(clusterStats);
    }

    /**
     * Put /text-clusters/{clusterId}
     * <p>
     * Sets a text cluster's disabled boolean value
     *
     * @param clusterId The id of the cluster to be disabled/enabled
     * @param disabled The predicate value defining the disabled state of the cluster
     * @return The status whether the boolean value was set successfully or the setting failed.
     */
    @PutMapping("/text-clusters/{clusterId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> toggleClusterDisabledPredicate(@PathVariable Long clusterId, @RequestParam boolean disabled) {
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
