package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ParticipationVCSAccessToken;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ParticipationVCSAccessTokenRepository;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCPersonalAccessTokenManagementService;

@Profile(PROFILE_CORE)
@Service
public class ParticipationVCSAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationVCSAccessTokenService.class);

    private final ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    public ParticipationVCSAccessTokenService(ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository) {
        this.participationVCSAccessTokenRepository = participationVCSAccessTokenRepository;
    }

    /**
     * Creates a vcs access token for a User,Participation pair and stores it in the database
     *
     * @param user          the user which is owner of the token
     * @param participation the participation which belongs to the token
     */
    public void createParticipationVCSAccessToken(User user, StudentParticipation participation) {
        ParticipationVCSAccessToken participationVCSAccessToken = new ParticipationVCSAccessToken();
        participationVCSAccessToken.setUser(user);
        participationVCSAccessToken.setParticipation(participation);
        participationVCSAccessToken.setVcsAccessToken(LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken());
        participationVCSAccessTokenRepository.save(participationVCSAccessToken);
    }

    /**
     * Retrieves the participationVCSAccessToken for a User,Participation pair if it exists
     *
     * @param userId          the user's id which is owner of the token
     * @param participationId the participation's id which the token belongs to
     * @return an Optional participationVCSAccessToken,
     */
    public Optional<ParticipationVCSAccessToken> findByUserIdAndParticipationId(Long userId, Long participationId) {
        return participationVCSAccessTokenRepository.findByUserIdAndParticipationId(userId, participationId);
    }

    /**
     * Deletes the token connected to a participation
     *
     * @param participationId the participation id for which the token should get deleted
     */
    public void deleteByParticipationId(long participationId) {
        participationVCSAccessTokenRepository.deleteByParticipation_id(participationId);
    }
}
