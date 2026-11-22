package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * A change of a working copy that runs while its lock is held, see {@link GitService#replaceWorkingCopyContent}.
 */
@FunctionalInterface
public interface WorkingCopyReplacement {

    void replace(Repository targetRepository) throws GitAPIException, IOException;
}
