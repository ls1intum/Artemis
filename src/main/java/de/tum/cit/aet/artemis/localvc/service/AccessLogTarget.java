package de.tum.cit.aet.artemis.localvc.service;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The repository an access log entry is written for. Most repositories belong to a participation, which the entry refers to. The tests and auxiliary repositories belong to the
 * exercise, so the entry refers to the exercise and to the repository.
 */
public sealed interface AccessLogTarget {

    /**
     * A repository that belongs to a participation: the repository of a student, the template or the solution.
     *
     * @param participationId the id of the participation
     */
    record OfParticipation(long participationId) implements AccessLogTarget {
    }

    /**
     * The tests or an auxiliary repository of an exercise.
     *
     * @param exerciseId            the id of the exercise the repository belongs to
     * @param repositoryType        {@link RepositoryType#TESTS} or {@link RepositoryType#AUXILIARY}
     * @param auxiliaryRepositoryId the id of the auxiliary repository, null for the tests repository
     * @param repositoryName        the name of the auxiliary repository, null for the tests repository
     */
    record OfStaffRepository(long exerciseId, RepositoryType repositoryType, @Nullable Long auxiliaryRepositoryId, @Nullable String repositoryName) implements AccessLogTarget {
    }
}
