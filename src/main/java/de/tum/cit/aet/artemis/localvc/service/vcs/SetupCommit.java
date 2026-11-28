package de.tum.cit.aet.artemis.localvc.service.vcs;

import java.util.Map;

import org.eclipse.jgit.lib.PersonIdent;
import org.jspecify.annotations.Nullable;

/**
 * What the single commit of a repository copy without history is made of, besides the state of the source.
 *
 * @param additionalFiles the files to add to the commit, by repository-relative path; they replace files of the source at the same path
 * @param author          the identity that authors and commits it, or null to keep the author of the first commit of the source
 */
public record SetupCommit(Map<String, byte[]> additionalFiles, @Nullable PersonIdent author) {

    /** A commit that adds nothing and keeps the author of the source. */
    public static final SetupCommit UNCHANGED = new SetupCommit(Map.of(), null);
}
