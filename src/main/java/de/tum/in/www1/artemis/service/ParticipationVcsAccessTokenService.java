package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ParticipationVCSAccessToken;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ParticipationVCSAccessTokenRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCPersonalAccessTokenManagementService;

@Profile(PROFILE_CORE)
@Service
public class ParticipationVcsAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationVcsAccessTokenService.class);

    private final ParticipationVCSAccessTokenRepository participationVcsAccessTokenRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public ParticipationVcsAccessTokenService(ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.participationVcsAccessTokenRepository = participationVCSAccessTokenRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
    }

    /**
     * Creates a vcs access token for a User,Participation pair and stores it in the database
     *
     * @param user          the user which is owner of the token
     * @param participation the participation which belongs to the token
     * @return the newly created ParticipationVCSAccessToken
     */
    public ParticipationVCSAccessToken createParticipationVCSAccessToken(User user, StudentParticipation participation) {
        ParticipationVCSAccessToken participationVCSAccessToken = new ParticipationVCSAccessToken();
        participationVCSAccessToken.setUser(user);
        participationVCSAccessToken.setParticipation(participation);
        participationVCSAccessToken.setVcsAccessToken(LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken());
        return participationVcsAccessTokenRepository.save(participationVCSAccessToken);
    }

    /**
     * Retrieves the participationVCSAccessToken for a User,Participation pair if it exists
     *
     * @param userId          the user's id which is owner of the token
     * @param participationId the participation's id which the token belongs to
     * @return an Optional participationVCSAccessToken,
     */
    public ParticipationVCSAccessToken findByUserIdAndParticipationIdOrElseThrow(Long userId, Long participationId) {
        return participationVcsAccessTokenRepository.findByUserIdAndParticipationIdOrElseThrow(userId, participationId);
    }

    /**
     * Checks if the participationVCSAccessToken for a User,Participation pair exists, and creates a new one if not
     *
     * @param user            the user's id which is owner of the token
     * @param participationId the participation's id which the token belongs to
     * @return an Optional participationVCSAccessToken,
     */
    public ParticipationVCSAccessToken createVcsAccessTokenForUserAndParticipationIdOrElseThrow(User user, long participationId) {
        participationVcsAccessTokenRepository.findByUserIdAndParticipationIdAndThrowIfExists(user.getId(), participationId);
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        return createParticipationVCSAccessToken(user, participation);
    }

    /**
     * Deletes the token connected to a participation
     *
     * @param participationId the participation id for which the token should get deleted
     */
    public void deleteByParticipationId(long participationId) {
        participationVcsAccessTokenRepository.deleteByParticipationId(participationId);
    }

    /**
     * Deletes all tokens for a given list of participations
     *
     * @param participations the participations for which the tokens should get deleted
     */
    public void deleteAllByParticipations(List<ProgrammingExerciseStudentParticipation> participations) {
        for (ProgrammingExerciseStudentParticipation participation : participations) {
            participationVcsAccessTokenRepository.deleteByParticipationId(participation.getId());
        }
    }

    /**
     * Deletes all participationVcsAccessTokens of a user
     *
     * @param userId The user's id
     */
    public void deleteAllByUserId(long userId) {
        participationVcsAccessTokenRepository.deleteAllByUserId(userId);
    }
}
