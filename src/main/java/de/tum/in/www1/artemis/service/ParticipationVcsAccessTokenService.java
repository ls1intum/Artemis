package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.DomainObject;
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
     */
    public void createParticipationVCSAccessToken(User user, StudentParticipation participation) {
        ParticipationVCSAccessToken participationVCSAccessToken = new ParticipationVCSAccessToken();
        participationVCSAccessToken.setUser(user);
        participationVCSAccessToken.setParticipation(participation);
        participationVCSAccessToken.setVcsAccessToken(LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken());
        participationVcsAccessTokenRepository.save(participationVCSAccessToken);
    }

    /**
     * Creates vcs tokens for StudentParticipations which do not yet have one
     *
     * @param participations The participations, for which tokens should be generated, if they do not already have one
     */
    public void createMissingParticipationVCSAccessTokens(List<ProgrammingExerciseStudentParticipation> participations) {
        try {
            var existingTokens = participationVcsAccessTokenRepository.findAllByParticipationIds(participations.stream().map(DomainObject::getId).toList());
            var participationsWithTokens = existingTokens.stream().map(ParticipationVCSAccessToken::getParticipation).toList();
            log.info("Create missing VcsAccessTokens for participationIds: {}", participations);
            List<ParticipationVCSAccessToken> vcsAccessTokens = new ArrayList<>();

            for (ProgrammingExerciseStudentParticipation participation : participations) {
                if (!participationsWithTokens.contains(participation) && participation.getParticipant() instanceof User user) {
                    var participationVCSAccessToken = new ParticipationVCSAccessToken();
                    participationVCSAccessToken.setUser(user);
                    participationVCSAccessToken.setParticipation(participation);
                    participationVCSAccessToken.setVcsAccessToken(LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken());
                    vcsAccessTokens.add(participationVCSAccessToken);
                }
            }

            log.info("Successfully generated {} missing VcsAccessTokens", vcsAccessTokens);
            participationVcsAccessTokenRepository.saveAll(vcsAccessTokens);
            log.info("Successfully saved missing VcsAccessTokens");
        }
        catch (Exception e) {
            log.error("Error creating missing VCS access tokens: {}", e.getMessage());
        }
    }

    /**
     * Retrieves the participationVCSAccessToken for a User,Participation pair if it exists
     *
     * @param userId          the user's id which is owner of the token
     * @param participationId the participation's id which the token belongs to
     * @return an Optional participationVCSAccessToken,
     */
    public ParticipationVCSAccessToken findByUserIdAndParticipationIdOrElseThrow(Long userId, Long participationId) {
        return participationVcsAccessTokenRepository.findByUserIdAndParticipationId(userId, participationId).orElseThrow(NoSuchElementException::new);
    }

    /**
     * Deletes the token connected to a participation
     *
     * @param participationId the participation id for which the token should get deleted
     */
    public void deleteByParticipationId(long participationId) {
        participationVcsAccessTokenRepository.deleteByParticipation_id(participationId);
    }

    public void deleteAllByParticipations(List<ProgrammingExerciseStudentParticipation> participations) {
        for (ProgrammingExerciseStudentParticipation participation : participations) {
            participationVcsAccessTokenRepository.deleteByParticipation_id(participation.getId());
        }
    }

    /**
     * Create missing participationVcsAccessTokens at application start
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createMissingParticipationVcsAccessToken() {
        log.info("Creating missing participation VCS access tokens");
        List<ProgrammingExerciseStudentParticipation> programmingExerciseStudentParticipations = programmingExerciseStudentParticipationRepository.findAll();
        createMissingParticipationVCSAccessTokens(programmingExerciseStudentParticipations);
    }

}
