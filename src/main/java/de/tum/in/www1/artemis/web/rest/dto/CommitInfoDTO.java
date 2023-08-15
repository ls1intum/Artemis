package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import org.eclipse.jgit.revwalk.RevCommit;

public record CommitInfoDTO(String hash, String message, ZonedDateTime timestamp, String author) {

    public static CommitInfoDTO of(RevCommit commit) {
        var authorIdent = commit.getAuthorIdent();
        var commitTime = authorIdent.getWhen();
        var timeZone = authorIdent.getTimeZone();
        var commitTimestamp = ZonedDateTime.ofInstant(commitTime.toInstant(), timeZone.toZoneId());

        return new CommitInfoDTO(commit.getId().getName(), commit.getFullMessage(), commitTimestamp, commit.getAuthorIdent().getName());
    }
}
