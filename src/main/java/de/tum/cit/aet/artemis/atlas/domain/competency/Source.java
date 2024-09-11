package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.domain.DomainObject;

@Entity
@Table(name = "source")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Source extends DomainObject {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "uri")
    private String uri;

    @OneToMany(mappedBy = "source", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("source")
    private Set<StandardizedCompetency> competencies = new HashSet<>();

    public Source() {

    }

    public Source(String title, String author, String uri) {
        this.title = title;
        this.author = author;
        this.uri = uri;
    }

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

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Set<StandardizedCompetency> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(Set<StandardizedCompetency> competencies) {
        this.competencies = competencies;
    }
}
