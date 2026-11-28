package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * An access log entry of the tests or an auxiliary repository of a programming exercise.
 * <p>
 * These repositories belong to the exercise and have no participation, so the entry refers to the exercise and to the repository instead. The entries of the template and
 * solution repositories, and of those of students, stay in {@link VcsAccessLog}, where they refer to the participation.
 */
@Entity
@Table(name = "vcs_staff_repository_access_log")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VcsStaffRepositoryAccessLog extends AbstractVcsAccessLogEntry {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    @Parent
    private ProgrammingExercise exercise;

    /** {@link RepositoryType#TESTS} or {@link RepositoryType#AUXILIARY}. */
    @Column(name = "repository_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RepositoryType repositoryType;

    /** The auxiliary repository, absent for the tests repository and once the auxiliary repository was removed from the exercise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auxiliary_repository_id")
    private AuxiliaryRepository auxiliaryRepository;

    /** The name of the auxiliary repository, which tells the entries of a removed one apart. Absent for the tests repository. */
    @Column(name = "repository_name")
    private String repositoryName;

    public VcsStaffRepositoryAccessLog() {
        // for Hibernate
    }

    /**
     * Creates an entry for an access that happened at the given time.
     *
     * @param exercise                the exercise the repository belongs to
     * @param repositoryType          the tests or an auxiliary repository
     * @param auxiliaryRepository     the auxiliary repository, null for the tests repository
     * @param repositoryName          the name of the auxiliary repository, null for the tests repository
     * @param user                    who accessed it
     * @param name                    the name of the user
     * @param email                   the email address of the user
     * @param repositoryActionType    what was done
     * @param authenticationMechanism how the user authenticated
     * @param commitHash              the commit that belongs to the action
     * @param ipAddress               where the access came from
     * @param timestamp               when it happened
     */
    public VcsStaffRepositoryAccessLog(ProgrammingExercise exercise, RepositoryType repositoryType, @Nullable AuxiliaryRepository auxiliaryRepository,
            @Nullable String repositoryName, User user, String name, String email, RepositoryActionType repositoryActionType, AuthenticationMechanism authenticationMechanism,
            String commitHash, String ipAddress, ZonedDateTime timestamp) {
        super(user, name, email, repositoryActionType, authenticationMechanism, commitHash, ipAddress, timestamp);
        this.exercise = exercise;
        this.repositoryType = repositoryType;
        this.auxiliaryRepository = auxiliaryRepository;
        this.repositoryName = repositoryName;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public RepositoryType getRepositoryType() {
        return repositoryType;
    }

    public AuxiliaryRepository getAuxiliaryRepository() {
        return auxiliaryRepository;
    }

    public String getRepositoryName() {
        return repositoryName;
    }
}
