package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.dto.ForwardedMessageDTO;
import de.tum.cit.aet.artemis.communication.dto.ForwardedMessagesGroupDTO;
import de.tum.cit.aet.artemis.communication.repository.ForwardedMessageRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

/**
 * REST controller for managing ForwardedMessages.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping(value = "api/communication/")
public class ForwardedMessageResource {

    private static final Logger log = LoggerFactory.getLogger(ForwardedMessageResource.class);

    private static final String ENTITY_NAME = "forwardedMessage";

    private final ForwardedMessageRepository forwardedMessageRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public ForwardedMessageResource(ForwardedMessageRepository forwardedMessageRepository, CourseRepository courseRepository, AuthorizationCheckService authCheckService) {
        this.forwardedMessageRepository = forwardedMessageRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /forwarded-messages : Create a new forwarded message.
     *
     * @param courseId            the ID of the course in which the forwarded message is being created.
     * @param forwardedMessageDTO the forwarded message to create.
     * @return the ResponseEntity with status 201 (Created) and with the body containing the new forwarded message,
     *         or with status 400 (Bad Request) if the forwarded message already has an ID.
     * @throws BadRequestAlertException if the forwarded message already has an ID.
     */
    @PostMapping("forwarded-messages")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<ForwardedMessageDTO> createForwardedMessage(@RequestParam Long courseId, @RequestBody ForwardedMessageDTO forwardedMessageDTO) throws URISyntaxException {
        log.debug("POST createForwardedMessage invoked with forwardedMessageDTO: {}", forwardedMessageDTO.toString());
        long start = System.nanoTime();

        if (forwardedMessageDTO.id() != null) {
            throw new BadRequestAlertException("A new forwarded message cannot already have an ID", ENTITY_NAME, "idExists");
        }

        ForwardedMessage forwardedMessage = forwardedMessageDTO.toEntity();
        ForwardedMessage savedMessage = forwardedMessageRepository.save(forwardedMessage);

        log.info("createForwardedMessage took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.created(new URI("/api/communication/forwarded-messages/" + savedMessage.getId())).body(new ForwardedMessageDTO(savedMessage));
    }

    /**
     * GET /forwarded-messages : Retrieve forwarded messages grouped by their destination IDs.
     *
     * @param courseId   the ID of the course in which the forwarded message is being created.
     * @param postingIds a set of destination IDs (either post or answer IDs) for which forwarded messages should be retrieved.
     * @param type       the type of destination ('post' or 'answer') to specify whether the IDs belong to posts or answers.
     * @return the ResponseEntity with status 200 (OK) and a list of ForwardedMessagesGroupDTO objects,
     *         where each object contains a destination ID and the associated forwarded messages.
     * @throws BadRequestAlertException if the type parameter is invalid or unsupported.
     */
    @GetMapping("forwarded-messages")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<ForwardedMessagesGroupDTO>> getForwardedMessages(@RequestParam Long courseId, @RequestParam Set<Long> postingIds, @RequestParam PostingType type) {
        log.debug("GET getForwardedMessages invoked with postingIds {} and type {}", postingIds, type);
        long start = System.nanoTime();

        if (type != PostingType.POST && type != PostingType.ANSWER) {
            throw new BadRequestAlertException("Invalid type provided. Must be 'post' or 'answer'.", ENTITY_NAME, "invalidType");
        }

        Set<ForwardedMessage> forwardedMessages;
        if (type == PostingType.POST) {
            forwardedMessages = forwardedMessageRepository.findAllByDestinationPostIds(postingIds);
        }
        else { // type == PostingType.ANSWER
            forwardedMessages = forwardedMessageRepository.findAllByDestinationAnswerPostIds(postingIds);
        }

        List<ForwardedMessagesGroupDTO> result = forwardedMessages.stream()
                .collect(Collectors.groupingBy(fm -> type == PostingType.POST ? fm.getDestinationPost().getId() : fm.getDestinationAnswerPost().getId(),
                        Collectors.mapping(ForwardedMessageDTO::new, Collectors.toSet())))
                .entrySet().stream().map(entry -> new ForwardedMessagesGroupDTO(entry.getKey(), entry.getValue())).toList();

        log.info("getForwardedMessages took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok(result);
    }

}
