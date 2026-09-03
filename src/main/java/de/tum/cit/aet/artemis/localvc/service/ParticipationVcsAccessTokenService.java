package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ParticipationVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ParticipationVcsAccessTokenService {

    private final ParticipationVCSAccessTokenRepository participationVcsAccessTokenRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final TeamRepository teamRepository;

    public ParticipationVcsAccessTokenService(ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, TeamRepository teamRepository) {
        this.participationVcsAccessTokenRepository = participationVCSAccessTokenRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Creates a vcs access token for a User,Participation pair and stores it in the database
     *
     * @param user          the user which is owner of the token
     * @param participation the participation which belongs to the token
     * @return the newly created ParticipationVCSAccessToken
     */
    public ParticipationVCSAccessToken createParticipationVCSAccessToken(User user, StudentParticipation participation) {
        return createParticipationVCSAccessToken(user, participation, LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken());
    }

    /**
     * Creates a vcs access token for a User,Participation pair with a caller-supplied token value, instead of generating a fresh one.
     * <p>
     * Used when a participation shares another (canonical) participation's repository - e.g. a {@code UserStoryExercise}
     * participation reusing its {@code MilestoneExercise} group's repository (see {@code ParticipationService.startUserStoryExercise}
     * / {@code provisionUserStoryParticipationsForGroup}). Git authentication against the shared repository is
     * always checked against the *canonical* participation's stored token (the exercise resolved from the repository's
     * project key is the canonical one, and only its own participations are searched - see
     * {@code LocalVCServletService.tryAuthenticationWithParticipationVCSAccessToken}), so a sharing participation's token
     * must be an exact copy of the canonical one's value; an independently generated token would never match and clone
     * authentication would silently fail.
     *
     * @param user          the user which is owner of the token
     * @param participation the participation which belongs to the token
     * @param tokenValue    the token value to store (typically copied from the canonical participation's own token)
     * @return the newly created ParticipationVCSAccessToken
     */
    public ParticipationVCSAccessToken createParticipationVCSAccessToken(User user, StudentParticipation participation, String tokenValue) {
        ParticipationVCSAccessToken participationVCSAccessToken = new ParticipationVCSAccessToken();
        participationVCSAccessToken.setUser(user);
        participationVCSAccessToken.setParticipation(participation);
        participationVCSAccessToken.setVcsAccessToken(tokenValue);
        return participationVcsAccessTokenRepository.save(participationVCSAccessToken);
    }

    /**
     * Sets a participation's VCS access token to the given value, updating the existing row if one is already present
     * (e.g. one created by {@link #createParticipationVCSAccessToken(User, StudentParticipation)} before it was known
     * the participation would end up sharing a canonical participation's repository - see the class-level explanation
     * on {@link #createParticipationVCSAccessToken(User, StudentParticipation, String)}) rather than inserting a second,
     * ambiguous row for the same user/participation pair.
     *
     * @param user          the user which is owner of the token
     * @param participation the participation which belongs to the token
     * @param tokenValue    the token value to store (typically copied from the canonical participation's own token)
     */
    public void setParticipationVCSAccessTokenValue(User user, StudentParticipation participation, String tokenValue) {
        Optional<ParticipationVCSAccessToken> existing = participation.getId() == null ? Optional.empty()
                : participationVcsAccessTokenRepository.findByUserIdAndParticipationId(user.getId(), participation.getId());
        if (existing.isPresent()) {
            existing.get().setVcsAccessToken(tokenValue);
            participationVcsAccessTokenRepository.save(existing.get());
        }
        else {
            createParticipationVCSAccessToken(user, participation, tokenValue);
        }
    }

    /**
     * Looks up the raw token value stored for a user/participation pair, without the ownership check
     * {@link #findByUserAndParticipationIdOrElseThrow} performs - used internally to copy a canonical participation's
     * token onto a sharing participation (see {@link #createParticipationVCSAccessToken(User, StudentParticipation, String)}).
     *
     * @param userId          the id of the token's owning user
     * @param participationId the id of the participation the token belongs to
     * @return the token value, or empty if the user has no token for that participation
     */
    public Optional<String> findTokenValue(long userId, long participationId) {
        return participationVcsAccessTokenRepository.findByUserIdAndParticipationId(userId, participationId).map(ParticipationVCSAccessToken::getVcsAccessToken);
    }

    /**
     * Retrieves the participationVCSAccessToken for a User,Participation pair if it exists and if the user owns the participation
     *
     * @param user            the user which is owner of the token
     * @param participationId the participation's id which the token belongs to
     * @return an Optional participationVCSAccessToken,
     */
    public ParticipationVCSAccessToken findByUserAndParticipationIdOrElseThrow(User user, long participationId) {
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        loadTeamStudentsForTeamExercise(participation);
        if (participation.isOwnedBy(user)) {
            return participationVcsAccessTokenRepository.findByUserIdAndParticipationIdOrElseThrow(user.getId(), participationId);
        }
        else {
            throw new AccessForbiddenException("Participation not owned by user");
        }
    }

    /**
     * Checks if the participationVCSAccessToken for a User,Participation pair exists, and creates a new one if not; if the user owns the participation
     *
     * @param user            the user's id which is owner of the token
     * @param participationId the participation's id which the token belongs to
     * @return an Optional participationVCSAccessToken,
     */
    public ParticipationVCSAccessToken createVcsAccessTokenForUserAndParticipationIdOrElseThrow(User user, long participationId) {
        participationVcsAccessTokenRepository.findByUserIdAndParticipationIdAndThrowIfExists(user.getId(), participationId);
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        loadTeamStudentsForTeamExercise(participation);
        if (participation.isOwnedBy(user)) {
            return createParticipationVCSAccessToken(user, participation);
        }
        else {
            throw new AccessForbiddenException("Participation not owned by user");
        }
    }

    /**
     * Loads the team students of a participation's team, if it has a team
     *
     * @param participation the participation which team's students are not loaded yet
     */
    private void loadTeamStudentsForTeamExercise(StudentParticipation participation) {
        if (participation.getTeam().isPresent()) {
            Team team = participation.getTeam().get();
            Team teamWithStudents = teamRepository.findWithStudentsByIdElseThrow(team.getId());
            participation.getTeam().get().setStudents(teamWithStudents.getStudents());
        }
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
     * Deletes all participationVcsAccessTokens of a user
     *
     * @param userId The user's id
     */
    public void deleteAllByUserId(long userId) {
        participationVcsAccessTokenRepository.deleteAllByUserId(userId);
    }
}
