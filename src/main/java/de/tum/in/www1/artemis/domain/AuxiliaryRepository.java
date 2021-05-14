package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.net.MalformedURLException;

@Entity
@Table(name = "programming_exercise_auxiliary_repositories")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AuxiliaryRepository extends DomainObject {

    // The name must not be the same as a name registered in the course
    @Column(name = "name")
    private String name;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(name = "checkout_directory")
    private String checkoutDirectory;

    @Column(name = "description")
    private String description;

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

    /**
     * Gets a URL of the  templateRepositoryUrl if there is one
     *
     * @return a URL object of the  templateRepositoryUrl or null if there is no templateRepositoryUrl
     */
    @JsonIgnore
    public VcsRepositoryUrl getVcsTemplateRepositoryUrl() {
        String repositoryUrl = getRepositoryUrl();
        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            return null;
        }

        try {
            return new VcsRepositoryUrl(repositoryUrl);
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
