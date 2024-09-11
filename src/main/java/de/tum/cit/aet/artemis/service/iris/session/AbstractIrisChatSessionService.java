package de.tum.cit.aet.artemis.service.iris.session;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.domain.iris.session.IrisChatSession;
import de.tum.cit.aet.artemis.repository.iris.IrisSessionRepository;

public abstract class AbstractIrisChatSessionService<S extends IrisChatSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface {

    private final IrisSessionRepository irisSessionRepository;

    private final ObjectMapper objectMapper;

    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository, ObjectMapper objectMapper) {
        this.irisSessionRepository = irisSessionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Updates the latest suggestions of the session.
     * Converts the list of latest suggestions to a JSON string.
     * The updated suggestions are then saved to the session in the database.
     *
     * @param session           The session to update
     * @param latestSuggestions The latest suggestions to set
     */
    protected void updateLatestSuggestions(S session, List<String> latestSuggestions) {
        if (latestSuggestions == null || latestSuggestions.isEmpty()) {
            return;
        }
        try {
            var suggestions = objectMapper.writeValueAsString(latestSuggestions);
            session.setLatestSuggestions(suggestions);
            irisSessionRepository.save(session);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Could not update latest suggestions for session " + session.getId(), e);
        }
    }
}
