package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.dto.ForwardedMessageDTO;
import de.tum.cit.aet.artemis.communication.repository.ForwardedMessageRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * REST controller for managing ForwardedMessages.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping(value = "api/")
public class ForwardedMessageResource {

    private static final Logger log = LoggerFactory.getLogger(ForwardedMessageResource.class);

    private static final String ENTITY_NAME = "forwardedMessage";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ForwardedMessageRepository forwardedMessageRepository;

    public ForwardedMessageResource(ForwardedMessageRepository forwardedMessageRepository) {
        this.forwardedMessageRepository = forwardedMessageRepository;
    }

    /**
     * POST /forwarded-messages : Create a new forwarded message.
     *
     * @param forwardedMessage the forwarded message to create
     * @return the ResponseEntity with status 201 (Created) and with body the new forwarded message,
     *         or with status 400 (Bad Request) if the forwarded message has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("forwarded-messages")
    public ResponseEntity<ForwardedMessageDTO> createForwardedMessage(@RequestBody ForwardedMessage forwardedMessage) throws URISyntaxException {
        log.debug("REST request to save ForwardedMessage : {}");
        if (forwardedMessage.getId() != null) {
            throw new BadRequestAlertException("A new forwarded message cannot already have an ID", ENTITY_NAME, "idExists");
        }
        ForwardedMessage savedMessage = forwardedMessageRepository.save(forwardedMessage);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(savedMessage);
        return ResponseEntity.created(new URI("/api/forwarded-messages/" + savedMessage.getId())).body(dto);
    }

    /**
     * GET /forwarded-messages/{id} : get the forwarded message by id.
     *
     * @param id the id of the forwarded message to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the forwarded message, or with status 404 (Not Found)
     */
    /*
     * @GetMapping("/{id}")
     * public ResponseEntity<ForwardedMessageDTO> getForwardedMessage(@PathVariable String id) {
     * log.debug("REST request to get ForwardedMessage : {}", id);
     * ForwardedMessage forwardedMessage = forwardedMessageRepository.findById((long) Integer.parseInt(id))
     * .orElseThrow(() -> new BadRequestAlertException("Forwarded message not found", ENTITY_NAME, "idNotFound"));
     * ForwardedMessageDTO dto = new ForwardedMessageDTO(forwardedMessage);
     * return ResponseEntity.ok(dto);
     * }
     */

    /**
     * DELETE /forwarded-messages/{id} : delete the forwarded message by id.
     *
     * @param id the id of the forwarded message to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    /*
     * @DeleteMapping("/{id}")
     * public ResponseEntity<Void> deleteForwardedMessage(@PathVariable Long id) {
     * log.debug("REST request to delete ForwardedMessage : {}", id);
     * forwardedMessageRepository.deleteById(id);
     * return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
     * }
     */

    /**
     * GET /forwarded-messages/destination-post/{destinationPostId} : get all forwarded messages linked to a specific destination post ID.
     *
     * @param destinationPostId the ID of the destination post
     * @return the ResponseEntity with status 200 (OK) and the list of forwarded messages in body
     */
    /*
     * @GetMapping("/destination-post/{destinationPostId}")
     * public ResponseEntity<Set<ForwardedMessageDTO>> getForwardedMessagesByDestinationPostId(@PathVariable Long destinationPostId) {
     * log.debug("REST request to get ForwardedMessages by destinationPostId : {}", destinationPostId);
     * Set<ForwardedMessageDTO> dtos = forwardedMessageRepository.findAllByDestinationPostId(destinationPostId).stream()
     * .map(ForwardedMessageDTO::new)
     * .collect(Collectors.toSet());
     * return ResponseEntity.ok(dtos);
     * }
     */

    /**
     * GET /forwarded-messages/destination-answer/{destinationAnswerId} : get all forwarded messages linked to a specific destination answer ID.
     *
     * @param destinationAnswerId the ID of the destination answer post
     * @return the ResponseEntity with status 200 (OK) and the list of forwarded messages in body
     */
    /*
     * @GetMapping("/destination-answer/{destinationAnswerId}")
     * public ResponseEntity<Set<ForwardedMessageDTO>> getForwardedMessagesByDestinationAnswerId(@PathVariable Long destinationAnswerId) {
     * log.debug("REST request to get ForwardedMessages by destinationAnswerId : {}", destinationAnswerId);
     * Set<ForwardedMessageDTO> dtos = forwardedMessageRepository.findAllByDestinationAnswerId(destinationAnswerId).stream()
     * .map(ForwardedMessageDTO::new)
     * .collect(Collectors.toSet());
     * return ResponseEntity.ok(dtos);
     * }
     */

}
