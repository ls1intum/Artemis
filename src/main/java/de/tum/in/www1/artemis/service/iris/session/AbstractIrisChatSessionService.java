package de.tum.in.www1.artemis.service.iris.session;

import java.util.List;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;

public abstract class AbstractIrisChatSessionService<S extends IrisChatSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface {

    protected IrisSessionRepository irisSessionRepository;

    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository) {
        this.irisSessionRepository = irisSessionRepository;
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

        var suggestions = latestSuggestions.stream().map(s -> s.replace("||", "\\||")).collect(Collectors.joining("||"));

        session.setLatestSuggestions(suggestions);
        irisSessionRepository.save(session);
    }
}
