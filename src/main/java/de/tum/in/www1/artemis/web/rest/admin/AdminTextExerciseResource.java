package de.tum.in.www1.artemis.web.rest.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

/**
 * REST controller for administrating TextExercise.
 */
@RestController
@RequestMapping("api/admin/")
public class AdminTextExerciseResource {

    private final InstanceMessageSendService instanceMessageSendService;

    public AdminTextExerciseResource(InstanceMessageSendService instanceMessageSendService) {
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * POST /text-exercises/{exerciseId}/trigger-automatic-assessment: trigger automatic assessment
     * (clustering task) for given exercise id As the clustering can be performed on a different
     * node, this will always return 200, despite an error could occur on the other node.
     *
     * @param exerciseId id of the exercised that for which the automatic assessment should be
     *                   triggered
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("text-exercises/{exerciseId}/trigger-automatic-assessment")
    @EnforceAdmin
    public ResponseEntity<Void> triggerAutomaticAssessment(@PathVariable Long exerciseId) {
        instanceMessageSendService.sendTextExerciseInstantClustering(exerciseId);
        return ResponseEntity.ok().build();
    }
}
