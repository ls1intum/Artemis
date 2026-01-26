package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDataDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryWithRelationsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;

/**
 * REST controller for Memiris memories.
 */
@Profile(PROFILE_IRIS)
@FeatureToggle(Feature.Memiris)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisMemoryResource {

    private final UserRepository userRepository;

    private final PyrisConnectorService pyrisConnectorService;

    public IrisMemoryResource(UserRepository userRepository, PyrisConnectorService pyrisConnectorService) {
        this.userRepository = userRepository;
        this.pyrisConnectorService = pyrisConnectorService;
    }

    /**
     * GET iris/user/memory-data: Retrieve aggregated Memiris memory data for the current user.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and body the aggregated memory data
     */
    @GetMapping("user/memory-data")
    @EnforceAtLeastStudent
    public ResponseEntity<MemirisMemoryDataDTO> listMemoryData() {
        var user = userRepository.getUser();
        var memories = pyrisConnectorService.listMemirisMemoryData(user.getId());
        return ResponseEntity.ok(memories);
    }

    /**
     * DELETE iris/user/memory/{memoryId}: Delete a specific Memiris memory for the current user.
     *
     * @param memoryId the id of the memory to delete
     * @return the {@link ResponseEntity} with status {@code 204 (No Content)}
     */
    @DeleteMapping("user/memory/{memoryId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteMemory(@PathVariable String memoryId) {
        var user = userRepository.getUser();
        pyrisConnectorService.deleteMemirisMemory(user.getId(), memoryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET iris/user/memory/{memoryId}: Retrieve a Memiris memory with its learnings and connections.
     *
     * @param memoryId the id of the memory to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and body the flattened memory DTO,
     *         or {@code 404 (Not Found)} if the memory does not exist
     */
    @GetMapping("user/memory/{memoryId}")
    @EnforceAtLeastStudent
    public ResponseEntity<MemirisMemoryWithRelationsDTO> getMemoryWithRelations(@PathVariable String memoryId) {
        var user = userRepository.getUser();
        var dto = pyrisConnectorService.getMemirisMemoryWithRelations(user.getId(), memoryId);
        return ResponseEntity.ok(dto);
    }
}
