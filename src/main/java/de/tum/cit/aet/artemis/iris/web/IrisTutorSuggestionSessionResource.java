package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.repository.IrisTutorSuggestionSessionRepository;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;

/**
 * REST controller for managing Iris tutor suggestion sessions.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/iris/tutor-suggestion/")
public class IrisTutorSuggestionSessionResource {

    protected final PostRepository postRepository;

    private final UserRepository userRepository;

    private final IrisTutorSuggestionSessionRepository irisTutorSuggestionSessionRepository;

    protected IrisTutorSuggestionSessionResource(PostRepository postRepository, IrisTutorSuggestionSessionService irisTutorSuggestionSessionService, UserRepository userRepository,
            IrisTutorSuggestionSessionRepository irisTutorSuggestionSessionRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.irisTutorSuggestionSessionRepository = irisTutorSuggestionSessionRepository;
    }

    /**
     * POST /{postId}/sessions/current : Get the current session for the post or create a new one if it does not exist.
     *
     * @param postId post ID
     * @return the ResponseEntity with status 200 (OK) and the current session, or status 201 (Created) and the new session
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("{postId}/sessions/current")
    // @EnforceAtLeastTutorInCourse
    public ResponseEntity<IrisTutorSuggestionSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long postId) throws URISyntaxException {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // TODO: Add Iris settings activated for tutor suggestions check

        var sessionOptional = irisTutorSuggestionSessionRepository.findLatestSessionsByPostIdAndUserIdWithMessages(postId, user.getId(), Pageable.ofSize(1)).stream().findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            return ResponseEntity.ok(session);
        }
        return createSessionForPost(postId);
    }

    /**
     * POST /{postId}/sessions : Create a new session for the post.
     *
     * @param postId post ID
     * @return the ResponseEntity with status 201 (Created) and the new session
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("{postId}/sessions")
    public ResponseEntity<IrisTutorSuggestionSession> createSessionForPost(@PathVariable Long postId) throws URISyntaxException {
        var post = postRepository.findPostOrMessagePostByIdElseThrow(postId);

        // TODO: Add Iris settings activated for tutor suggestions check

        var user = userRepository.getUserWithGroupsAndAuthorities();

        var session = irisTutorSuggestionSessionRepository.save(new IrisTutorSuggestionSession(post, user));
        var uriString = "/api/iris/sessions/" + session.getId();

        return ResponseEntity.created(new URI(uriString)).body(session);
    }

}
