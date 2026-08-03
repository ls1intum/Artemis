package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * A repository-scoped VCS access token for course staff (tutors, editors, instructors).
 * <p>
 * The token is bound to exactly one repository of a programming exercise: either a base repository (template, tests, solution or one auxiliary repository), or a student
 * assignment repository ({@link RepositoryType#USER}), in which case {@link #participation} points to the owning student participation. It only authenticates the owning staff
 * user; the actual authorization (at least tutor to read, at least editor to write) is still enforced on every git operation. The repository the token is valid for is identified
 * by its canonical {@code repositoryUri}.
 * <p>
 * In contrast, a {@link ParticipationVCSAccessToken} is the token a <em>student</em> owns for their <em>own</em> participation.
 */
@Entity
@Table(name = "repository_vcs_access_token", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "repository_uri" }) })
public class RepositoryVCSAccessToken extends DomainObject {

    // All associations are lazy: the hot authentication path (lookup by user + repository URI on every git operation) only needs the token value, never the related entities.
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProgrammingExercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "repository_type")
    private RepositoryType repositoryType;

    /**
     * Only set when {@link #repositoryType} is {@link RepositoryType#AUXILIARY}; otherwise {@code null}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private AuxiliaryRepository auxiliaryRepository;

    /**
     * The student participation this token grants access to. Only set when {@link #repositoryType} is {@link RepositoryType#USER} (a student assignment repository); otherwise
     * {@code null}. Cascades on delete at the database level, so removing the participation automatically removes the staff tokens for its repository.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private Participation participation;

    @Column(name = "repository_uri", length = 500)
    private String repositoryUri;

    @Column(name = "vcs_access_token", length = 50)
    private String vcsAccessToken;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public RepositoryType getRepositoryType() {
        return repositoryType;
    }

    public void setRepositoryType(RepositoryType repositoryType) {
        this.repositoryType = repositoryType;
    }

    public AuxiliaryRepository getAuxiliaryRepository() {
        return auxiliaryRepository;
    }

    public void setAuxiliaryRepository(AuxiliaryRepository auxiliaryRepository) {
        this.auxiliaryRepository = auxiliaryRepository;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public String getRepositoryUri() {
        return repositoryUri;
    }

    public void setRepositoryUri(String repositoryUri) {
        this.repositoryUri = repositoryUri;
    }

    public String getVcsAccessToken() {
        return vcsAccessToken;
    }

    public void setVcsAccessToken(String vcsAccessToken) {
        this.vcsAccessToken = vcsAccessToken;
    }
}
