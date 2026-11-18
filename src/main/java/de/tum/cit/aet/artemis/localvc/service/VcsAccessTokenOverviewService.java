package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessTokenType;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessTokenOverviewDTO;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;

/**
 * Provides a user with an overview of the VCS access tokens they own (participation tokens plus repository-scoped staff tokens) and lets them revoke individual tokens. Only ever
 * operates on the current user's own tokens; it never exposes the token secret, only display metadata.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class VcsAccessTokenOverviewService {

    private final ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    private final RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    public VcsAccessTokenOverviewService(ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository,
            RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository) {
        this.participationVCSAccessTokenRepository = participationVCSAccessTokenRepository;
        this.repositoryVCSAccessTokenRepository = repositoryVCSAccessTokenRepository;
    }

    /**
     * Returns all VCS access tokens the given user owns (participation and repository-scoped) as display-only overview projections, in a stable order (by exercise title, then
     * token type, then id). The number of tokens per user is small, so the two token tables are merged and sorted in application code.
     *
     * @param userId the id of the user whose tokens are listed
     * @return the user's tokens as overview DTOs
     */
    public List<VcsAccessTokenOverviewDTO> getTokenOverviewForUser(long userId) {
        List<VcsAccessTokenOverviewDTO> tokens = new ArrayList<>(participationVCSAccessTokenRepository.findOverviewsByUserId(userId));
        tokens.addAll(repositoryVCSAccessTokenRepository.findOverviewsByUserId(userId));
        tokens.sort(Comparator.comparing(VcsAccessTokenOverviewDTO::courseTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(VcsAccessTokenOverviewDTO::exerciseTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(VcsAccessTokenOverviewDTO::tokenType)
                .thenComparingLong(VcsAccessTokenOverviewDTO::id));
        return tokens;
    }

    /**
     * Revokes (deletes) a single token the user owns. The delete is scoped to {@code (id, userId)}, so a user can never revoke another user's token. The next clone-dialog visit
     * transparently re-mints a fresh token via the existing get-or-create flow.
     *
     * @param userId    the id of the user revoking one of their own tokens
     * @param tokenType whether the token lives in the participation or the repository token table
     * @param tokenId   the id of the token to revoke
     * @throws EntityNotFoundException if the user does not own a token of that type with that id
     */
    public void revokeToken(long userId, VcsAccessTokenType tokenType, long tokenId) {
        int deletedRows = switch (tokenType) {
            case PARTICIPATION -> participationVCSAccessTokenRepository.deleteByIdAndUserId(tokenId, userId);
            case REPOSITORY -> repositoryVCSAccessTokenRepository.deleteByIdAndUserId(tokenId, userId);
        };
        if (deletedRows == 0) {
            throw new EntityNotFoundException("VCS access token", tokenId);
        }
    }
}
