package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
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
import de.tum.cit.aet.artemis.communication.dto.ForwardedMessagesGroupDTO;
import de.tum.cit.aet.artemis.communication.repository.ForwardedMessageRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

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
     * @param forwardedMessage the forwarded message to create.
     * @return the ResponseEntity with status 201 (Created) and with the body containing the new forwarded message,
     *         or with status 400 (Bad Request) if the forwarded message already has an ID.
     * @throws BadRequestAlertException if the forwarded message already has an ID.
     */
    @PostMapping("/forwarded-messages")
    public ResponseEntity<ForwardedMessageDTO> createForwardedMessage(@RequestBody ForwardedMessage forwardedMessage) throws URISyntaxException {
        if (forwardedMessage.getId() != null) {
            throw new BadRequestAlertException("A new forwarded message cannot already have an ID", ENTITY_NAME, "idExists");
        }
        ForwardedMessage savedMessage = forwardedMessageRepository.save(forwardedMessage);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(savedMessage);
        return ResponseEntity.created(new URI("/api/forwarded-messages/" + savedMessage.getId())).body(dto);
    }

    /**
     * GET /forwarded-messages : Retrieve forwarded messages grouped by their destination IDs.
     *
     * @param ids  a set of destination IDs (either post or answer IDs) for which forwarded messages should be retrieved.
     * @param type the type of destination ('post' or 'answer') to specify whether the IDs belong to posts or answers.
     * @return the ResponseEntity with status 200 (OK) and a list of ForwardedMessagesGroupDTO objects,
     *         where each object contains a destination ID and the associated forwarded messages.
     * @throws BadRequestAlertException if the type parameter is invalid or unsupported.
     */
    @GetMapping("/forwarded-messages")
    public ResponseEntity<List<ForwardedMessagesGroupDTO>> getForwardedMessages(@RequestParam Set<Long> ids, @RequestParam String type) {

        log.debug("GET getForwardedMessages invoked with ids {} and type {}", ids, type);
        long start = System.nanoTime();

        if (!"post".equalsIgnoreCase(type) && !"answer".equalsIgnoreCase(type)) {
            throw new BadRequestAlertException("Invalid type provided. Must be 'post' or 'answer'.", "ForwardedMessage", "invalidType");
        }

        Set<ForwardedMessage> forwardedMessages;
        if ("post".equalsIgnoreCase(type)) {
            forwardedMessages = forwardedMessageRepository.findAllByDestinationPostIds(ids);
        }
        else {
            forwardedMessages = forwardedMessageRepository.findAllByDestinationAnswerPostIds(ids);
        }

        List<ForwardedMessagesGroupDTO> result = forwardedMessages.stream()
                .collect(Collectors.groupingBy(fm -> "post".equalsIgnoreCase(type) ? fm.getDestinationPost().getId() : fm.getDestinationAnswerPost().getId(),
                        Collectors.mapping(ForwardedMessageDTO::new, Collectors.toSet())))
                .entrySet().stream().map(entry -> new ForwardedMessagesGroupDTO(entry.getKey(), entry.getValue())).toList();

        log.info("getForwardedMessages took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok(result);
    }

}
