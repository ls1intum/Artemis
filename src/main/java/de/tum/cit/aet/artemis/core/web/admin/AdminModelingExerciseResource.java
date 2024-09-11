package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.modeling.repository.ModelClusterRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseService;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for administrating ModelingExercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminModelingExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(AdminModelingExerciseResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "modelingExercise";

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingExerciseService modelingExerciseService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ModelClusterRepository modelClusterRepository;

    public AdminModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository, ModelingExerciseService modelingExerciseService,
            InstanceMessageSendService instanceMessageSendService, ModelClusterRepository modelClusterRepository) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseService = modelingExerciseService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.modelClusterRepository = modelClusterRepository;
    }

    /**
     * GET /modeling-exercises/:id/check-clusters : delete the clusters and elements of "id" modelingExercise.
     *
     * @param exerciseId the id of the modelingExercise to delete clusters and elements
     * @return the ResponseEntity with status 200 (OK)
     */
    @GetMapping("modeling-exercises/{exerciseId}/check-clusters")
    @EnforceAdmin
    public ResponseEntity<Integer> checkClusters(@PathVariable Long exerciseId) {
        log.info("REST request to check clusters of ModelingExercise : {}", exerciseId);
        int clusterCount = modelClusterRepository.countByExerciseIdWithEagerElements(exerciseId);
        return ResponseEntity.ok().body(clusterCount);
    }

    /**
     * DELETE /modeling-exercises/:id/clusters : delete the clusters and elements of "id" modelingExercise.
     *
     * @param exerciseId the id of the modelingExercise to delete clusters and elements
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("modeling-exercises/{exerciseId}/clusters")
    @EnforceAdmin
    public ResponseEntity<Void> deleteModelingExerciseClustersAndElements(@PathVariable Long exerciseId) {
        log.info("REST request to delete ModelingExercise : {}", exerciseId);
        var modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);

        modelingExerciseService.deleteClustersAndElements(modelingExercise);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, modelingExercise.getTitle())).build();
    }

    /**
     * POST /modeling-exercises/{exerciseId}/trigger-automatic-assessment: trigger automatic assessment
     * (clustering task) for given exercise id As the clustering can be performed on a different
     * node, this will always return 200, despite an error could occur on the other node.
     *
     * @param exerciseId id of the exercised that for which the automatic assessment should be triggered
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("modeling-exercises/{exerciseId}/trigger-automatic-assessment")
    @EnforceAdmin
    public ResponseEntity<Void> triggerAutomaticAssessment(@PathVariable Long exerciseId) {
        instanceMessageSendService.sendModelingExerciseInstantClustering(exerciseId);
        return ResponseEntity.ok().build();
    }
}
