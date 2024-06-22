package de.tum.in.www1.artemis.service.iris.session;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;

public abstract class AbstractIrisChatSessionService<S extends IrisChatSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface {

    protected IrisSessionRepository irisSessionRepository;

    protected final ObjectMapper objectMapper;

    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository) {
        this.irisSessionRepository = irisSessionRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Updates the latest suggestions of the session.
     * Concatenates the suggestions with "||" as separator.
     * If a suggestion already contains "||", they are replaced with "\\||", before joining.
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
