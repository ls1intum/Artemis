package de.tum.cit.aet.artemis.proof.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.proof.config.ProofEnabled;
import de.tum.cit.aet.artemis.proof.dto.BlockDefinitionDTO;
import de.tum.cit.aet.artemis.proof.service.BlockRegistry;

@Lazy
@Conditional(ProofEnabled.class)
@RestController
@RequestMapping("api/proof/")
public class BlockRegistryResource {

    private static final Logger log = LoggerFactory.getLogger(BlockRegistryResource.class);

    private final BlockRegistry blockRegistry;

    public BlockRegistryResource(BlockRegistry blockRegistry) {
        this.blockRegistry = blockRegistry;
    }

    /**
     * GET /block-registry : returns all registered block types with their rewrite rules.
     *
     * @return list of block definitions
     */
    @GetMapping("block-registry")
    @EnforceAtLeastEditor
    public ResponseEntity<List<BlockDefinitionDTO>> getBlockRegistry() {
        log.debug("REST request to get block registry");
        List<BlockDefinitionDTO> dtos = blockRegistry.getAllBlocks().stream().map(b -> BlockDefinitionDTO.of(b, blockRegistry.getNormalizedRulesFor(b))).toList();
        return ResponseEntity.ok(dtos);
    }
}
