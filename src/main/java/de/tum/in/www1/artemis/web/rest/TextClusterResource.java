package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextClusterStatistics;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing TextClusterResource.
 */
@RestController
@RequestMapping("/api")
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
     * Get /text-exercises/{exerciseId}/cluster-statistics/
     * <p>
     * Get text cluster stats
     * @param exerciseId The id of the text exercise to fetch cluster statistics data from
     * @return The list of cluster ids adjacent to their respective sizes and automatically graded text blocks
     */
    @GetMapping("/text-exercises/{exerciseId}/cluster-statistics")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TextClusterStatistics>> getClusterStats(@PathVariable Long exerciseId) {
        // Check if Instructor has permission to access the exercise with given exerciseId
        if (currentUserHasNoAccessToExercise(exerciseId)) {
            return forbidden();
        }

        List<TextClusterStatistics> clusterStats = textClusterRepository.getClusterStatistics(exerciseId);
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
        // Check if Instructor has permission to access the exercise that the cluster with id clusterId belongs to.
        TextCluster cluster = textClusterRepository.findWithEagerExerciseByIdElseThrow(clusterId);
        if (currentUserHasNoAccessToExercise(cluster.getExercise().getId())) {
            return forbidden();
        }

        log.info("REST request to disable Cluster : {}", clusterId);
        cluster.setDisabled(disabled);
        textClusterRepository.save(cluster);
        return ResponseEntity.ok().build();
    }

    /**
     * Checks if current user has access to the exercise with given exercise id
     * @param exerciseId the id of the exercise to check the predicate
     * @return true if user has access, false otherwise
     */
    private boolean currentUserHasNoAccessToExercise(Long exerciseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        return !authCheckService.isAtLeastInstructorForExercise(exercise, user);
    }
}
