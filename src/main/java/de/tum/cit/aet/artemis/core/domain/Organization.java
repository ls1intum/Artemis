package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "organization")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Organization extends DomainObject {

    @NonNull
    @Size(max = 100)
    @Column(name = "name", length = 100)
    private String name;

    @NonNull
    @Size(max = 50)
    @Column(name = "shortName", length = 50)
    private String shortName;

    @Column(name = "url")
    private String url;

    @Column(name = "description")
    private String description;

    @Column(name = "logoUrl")
    private String logoUrl;

    @NonNull
    @Column(name = "emailPattern")
    private String emailPattern;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getEmailPattern() {
        return emailPattern;
    }

    public void setEmailPattern(String emailPattern) {
        this.emailPattern = emailPattern;
    }
}
