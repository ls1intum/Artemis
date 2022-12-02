package de.tum.in.www1.artemis.web.rest;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.TextClusterStatisticsDTO;

/**
 * REST controller for managing TextClusterResource.
 */
@RestController
public class TextClusterResource {

    private final Logger log = LoggerFactory.getLogger(TextClusterResource.class);

    private final TextClusterRepository textClusterRepository;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public TextClusterResource(TextClusterRepository textClusterRepository, UserRepository userRepository, ExerciseRepository exerciseRepository,
            AuthorizationCheckService authCheckService) {
        this.textClusterRepository = textClusterRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Get text-exercises/{exerciseId}/cluster-statistics/
     *
     * Get text cluster stats
     * @param exerciseId The id of the text exercise to fetch cluster statistics data from
     * @return The list of cluster ids adjacent to their respective sizes and automatically graded text blocks
     */
    @GetMapping("text-exercises/{exerciseId}/cluster-statistics")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TextClusterStatisticsDTO>> getClusterStats(@PathVariable Long exerciseId) {
        // Check if instructor has permission to access the exercise with given exerciseId
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);

        // Get cluster statistics without the disabled state
        List<TextClusterStatisticsDTO> clusterStats = textClusterRepository.getClusterStatistics(exerciseId);

        // Fetch cluster with id and disabled state
        Map<Long, Boolean> clusterIdAndDisabled = textClusterRepository.getTextClusterWithIdAndDisabled();

        // Set cluster statistics disabled state
        clusterStats.forEach(s -> s.setDisabled(clusterIdAndDisabled.get(s.getClusterId())));
        log.debug("REST request to get clusterStats : {}", clusterStats);
        return ResponseEntity.ok().body(clusterStats);
    }

    /**
     * Patch text-exercises/{exerciseId}/text-clusters/{clusterId}
     * Sets a text cluster's disabled boolean value
     *
     * @param exerciseId The id of the exercise the cluster belongs to
     * @param clusterId The id of the cluster to be disabled/enabled
     * @param disabled The predicate value defining the disabled state of the cluster
     * @return The status whether the boolean value was set successfully or the setting failed.
     */
    @PatchMapping("text-exercises/{exerciseId}/text-clusters/{clusterId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> toggleClusterDisabledPredicate(@PathVariable Long exerciseId, @PathVariable Long clusterId, @RequestParam boolean disabled) {
        // Check if Instructor has permission to access the exercise that the cluster with id clusterId belongs to.
        TextCluster cluster = textClusterRepository.findWithEagerExerciseByIdElseThrow(clusterId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        // check if exercise exists and matches cluster exercise
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!exercise.getId().equals(cluster.getExercise().getId())) {
            return ResponseEntity.badRequest().build();
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, cluster.getExercise(), user);

        log.debug("REST request to disable Cluster : {}", clusterId);
        cluster.setDisabled(disabled);
        textClusterRepository.save(cluster);
        return ResponseEntity.ok().build();
    }
}
