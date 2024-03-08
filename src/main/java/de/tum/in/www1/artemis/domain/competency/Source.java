package de.tum.in.www1.artemis.domain.competency;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "source")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Source extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "uri")
    private URI uri;

    @OneToMany(mappedBy = "source", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("source")
    private Set<StandardizedCompetency> competencies = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public Set<StandardizedCompetency> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(Set<StandardizedCompetency> competencies) {
        this.competencies = competencies;
    }
}
