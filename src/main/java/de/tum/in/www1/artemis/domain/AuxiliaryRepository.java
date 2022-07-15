package de.tum.in.www1.artemis.domain;

import java.net.URISyntaxException;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

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
    public static final int MAX_REPOSITORY_URL_LENGTH = 500;

    @JsonIgnore
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    @JsonIgnore
    private static final Logger log = LoggerFactory.getLogger(AuxiliaryRepository.class);

    /**
     * Name of the repository.
     *
     * Must NOT be one of the following: exercise, solution or tests
     * One programming exercise must not have multiple repositories
     * sharing one name.
     */
    @Size(max = MAX_NAME_LENGTH)
    @Column(name = "name")
    private String name;

    @Size(max = MAX_REPOSITORY_URL_LENGTH)
    @Column(name = "repository_url")
    private String repositoryUrl;

    /**
     * One programming exercise must not have multiple repositories
     * sharing the same checkout directory. Bamboo does not allow that.
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

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
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
     * The repository is included when the repository url and the checkout directory have appropriate
     * values.
     *
     * @return true or false whether this repository should be included in the exercise build plan or not
     */
    @JsonIgnore
    public boolean shouldBeIncludedInBuildPlan() {
        return getCheckoutDirectory() != null && !getCheckoutDirectory().isBlank() && getRepositoryUrl() != null && !getRepositoryUrl().isBlank();
    }

    /**
     * Returns a copy of this auxiliary repository object without including the repository url.
     * This is used when importing auxiliary repositories from an old exercise to a new exercise.
     *
     * @return A clone of this auxiliary repository object except the repository url
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
     * Gets a URL of the repositoryUrl if there is one
     *
     * @return a URL object of the repositoryUrl or null if there is no repositoryUrl
     */
    @JsonIgnore
    public VcsRepositoryUrl getVcsRepositoryUrl() {
        String repositoryUrl = getRepositoryUrl();
        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            return null;
        }
        try {
            return new VcsRepositoryUrl(repositoryUrl);
        }
        catch (URISyntaxException e) {
            log.error("Malformed URI {} could not be used to instantiate VcsRepositoryUrl.", getRepositoryUrl(), e);
        }
        return null;
    }

    @Override
    public String toString() {
        return "AuxiliaryRepository{id=%d, name='%s', checkoutDirectory='%s', repositoryUrl='%s', description='%s', exercise=%s}".formatted(getId(), getName(),
                getCheckoutDirectory(), getRepositoryUrl(), getDescription(), exercise == null ? "null" : exercise.getId());
    }

    /**
     * Used in Bamboo Service to map the name of an auxiliary repository to its repository slug.
     */
    public record AuxRepoNameWithSlug(String name, String repositorySlug) {
    }

    public boolean containsEqualStringValues(AuxiliaryRepository other) {
        return Objects.equals(name, other.name) && Objects.equals(checkoutDirectory, other.checkoutDirectory) && Objects.equals(description, other.description);
    }
}
