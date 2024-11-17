package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@RequestMapping(value = "api")
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
    @PostMapping("/forwarded-messages")
    public ResponseEntity<ForwardedMessageDTO> createForwardedMessage(@RequestBody ForwardedMessage forwardedMessage) throws URISyntaxException {
        log.debug("REST request to save ForwardedMessage : {}");
        if (forwardedMessage.getId() != null) {
            throw new BadRequestAlertException("A new forwarded message cannot already have an ID", ENTITY_NAME, "idExists");
        }
        ForwardedMessage savedMessage = forwardedMessageRepository.save(forwardedMessage);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(savedMessage);
        return ResponseEntity.created(new URI("/api/forwarded-messages/" + savedMessage.getId())).body(dto);
    }

    @GetMapping("/forwarded-messages/posts")
    public ResponseEntity<Map<Long, Set<ForwardedMessageDTO>>> getForwardedMessagesByDestPostIds(@RequestParam Set<Long> dest_post_ids) {
        Set<ForwardedMessage> forwardedMessages = forwardedMessageRepository.findAllByDestinationPostIds(dest_post_ids);

        Map<Long, Set<ForwardedMessageDTO>> groupedDtos = forwardedMessages.stream()
                .collect(Collectors.groupingBy(fm -> fm.getDestinationPost().getId(), Collectors.mapping(ForwardedMessageDTO::new, Collectors.toSet())));

        return ResponseEntity.ok(groupedDtos);
    }

    @GetMapping("/forwarded-messages/answers")
    public ResponseEntity<Map<Long, Set<ForwardedMessageDTO>>> getForwardedMessagesByDestAnswerPostIds(@RequestParam Set<Long> dest_answer_post_ids) {
        Set<ForwardedMessage> forwardedMessages = forwardedMessageRepository.findAllByDestinationAnswerPostIds(dest_answer_post_ids);

        Map<Long, Set<ForwardedMessageDTO>> groupedDtos = forwardedMessages.stream()
                .collect(Collectors.groupingBy(fm -> fm.getDestinationAnswerPost().getId(), Collectors.mapping(ForwardedMessageDTO::new, Collectors.toSet())));

        return ResponseEntity.ok(groupedDtos);
    }

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

}
