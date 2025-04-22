package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import org.eclipse.jgit.revwalk.RevCommit;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a commit info.
 *
 * @param hash      the hash of the commit
 * @param message   the message of the commit
 * @param timestamp the timestamp of the commit
 * @param author    the author of the commit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommitInfoDTO(String hash, String message, ZonedDateTime timestamp, String author, String authorEmail) {

    /**
     * Creates a CommitInfoDTO from a RevCommit.
     *
     * @param commit the commit to create the DTO from
     * @return the created DTO
     */
    public static CommitInfoDTO of(RevCommit commit) {
        var authorIdent = commit.getAuthorIdent();
        var commitTimestamp = ZonedDateTime.ofInstant(authorIdent.getWhenAsInstant(), authorIdent.getZoneId());
        return new CommitInfoDTO(commit.getId().getName(), commit.getFullMessage(), commitTimestamp, commit.getAuthorIdent().getName(), commit.getAuthorIdent().getEmailAddress());
    }
}
