package de.tum.cit.aet.artemis.programming.domain;

import java.net.URISyntaxException;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "programming_exercise_auxiliary_repositories")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AuxiliaryRepository extends DomainObject {

    @JsonIgnore
    public static final int MAX_NAME_LENGTH = 100;

    @JsonIgnore
    public static final int MAX_CHECKOUT_DIRECTORY_LENGTH = 100;

    @JsonIgnore
    public static final int MAX_REPOSITORY_URI_LENGTH = 500;

    @JsonIgnore
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    @JsonIgnore
    private static final Logger log = LoggerFactory.getLogger(AuxiliaryRepository.class);

    /**
     * Name of the repository.
     * <p>
     * Must NOT be one of the following: exercise, solution or tests
     * One programming exercise must not have multiple repositories
     * sharing one name.
     */
    @Size(max = MAX_NAME_LENGTH)
    @Column(name = "name")
    private String name;

    @Size(max = MAX_REPOSITORY_URI_LENGTH)
    @Column(name = "repository_url")
    private String repositoryUri;

    /**
     * One programming exercise must not have multiple repositories
     * sharing the same checkout directory.
     */
    @Size(max = MAX_CHECKOUT_DIRECTORY_LENGTH)
    @Column(name = "checkout_directory")
    private String checkoutDirectory;

    @Size(max = MAX_DESCRIPTION_LENGTH)
    @Column(name = "description")
    private String description;

    @ManyToOne
    @JsonIgnore
    private ProgrammingExercise exercise;

    public String getRepositoryUri() {
        return repositoryUri;
    }

    public void setRepositoryUri(String repositoryUri) {
        this.repositoryUri = repositoryUri;
    }

    public String getCheckoutDirectory() {
        return checkoutDirectory;
    }

    public void setCheckoutDirectory(String checkoutDirectory) {
        this.checkoutDirectory = checkoutDirectory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    /**
     * Returns the fully qualified name of the repository, which looks like
     * [exercise identifier]-[repository name]
     *
     * @return Fully qualified name of the repository
     */
    @JsonIgnore
    public String getRepositoryName() {
        return exercise.generateRepositoryName(getName());
    }

    /**
     * Returns whether this auxiliary repository should be included in the exercise's build plan or not.
     * The repository is included when the repository uri and the checkout directory have appropriate
     * values.
     *
     * @return true or false whether this repository should be included in the exercise build plan or not
     */
    @JsonIgnore
    public boolean shouldBeIncludedInBuildPlan() {
        return getCheckoutDirectory() != null && !getCheckoutDirectory().isBlank() && getRepositoryUri() != null && !getRepositoryUri().isBlank();
    }

    /**
     * Returns a copy of this auxiliary repository object without including the repository uri.
     * This is used when importing auxiliary repositories from an old exercise to a new exercise.
     *
     * @return A clone of this auxiliary repository object except the repository uri
     */
    @JsonIgnore
    public AuxiliaryRepository cloneObjectForNewExercise() {
        AuxiliaryRepository newAuxiliaryRepository = new AuxiliaryRepository();
        newAuxiliaryRepository.name = this.name;
        newAuxiliaryRepository.checkoutDirectory = this.checkoutDirectory;
        newAuxiliaryRepository.description = this.description;
        return newAuxiliaryRepository;
    }

    /**
     * Gets a URL of the repositoryUri if there is one
     *
     * @return a URL object of the repositoryUri or null if there is no repositoryUri
     */
    @JsonIgnore
    public VcsRepositoryUri getVcsRepositoryUri() {
        String repositoryUri = getRepositoryUri();
        if (repositoryUri == null || repositoryUri.isEmpty()) {
            return null;
        }
        try {
            return new VcsRepositoryUri(repositoryUri);
        }
        catch (URISyntaxException e) {
            log.error("Malformed URI {} could not be used to instantiate VcsRepositoryUri.", getRepositoryUri(), e);
        }
        return null;
    }

    @Override
    public String toString() {
        return "AuxiliaryRepository{id=%d, name='%s', checkoutDirectory='%s', repositoryUri='%s', description='%s', exercise=%s}".formatted(getId(), getName(),
                getCheckoutDirectory(), getRepositoryUri(), getDescription(), exercise == null ? "null" : exercise.getId());
    }

    /**
     * Used to map the name of an auxiliary repository to its repository uri.
     */
    public record AuxRepoNameWithUri(String name, VcsRepositoryUri repositoryUri) {
    }

    public boolean containsEqualStringValues(AuxiliaryRepository other) {
        return Objects.equals(name, other.name) && Objects.equals(checkoutDirectory, other.checkoutDirectory) && Objects.equals(description, other.description);
    }
}
