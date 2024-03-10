package de.tum.in.www1.artemis.domain.competency;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "standardized_competency")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class StandardizedCompetency extends BaseCompetency {

    @Column(name = "version")
    private String version;

    @ManyToOne
    @JoinColumn(name = "knowledge_area_id")
    @JsonIgnoreProperties("competencies")
    private KnowledgeArea knowledgeArea;

    @ManyToOne
    @JoinColumn(name = "source_id")
    @JsonIgnoreProperties("competencies")
    private Source source;

    @ManyToOne
    @JoinColumn(name = "first_version_id")
    @JsonBackReference
    private StandardizedCompetency firstVersion;

    @OneToMany(mappedBy = "firstVersion", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private Set<StandardizedCompetency> childVersions = new HashSet<>();

    @OneToMany(mappedBy = "linkedStandardizedCompetency", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("linkedStandardizedCompetency")
    private Set<Competency> linkedCompetencies = new HashSet<>();

    public StandardizedCompetency(String title, String description, CompetencyTaxonomy taxonomy, String version) {
        super(title, description, taxonomy);
        this.version = version;
    }

    public StandardizedCompetency() {

    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public KnowledgeArea getKnowledgeArea() {
        return knowledgeArea;
    }

    public void setKnowledgeArea(KnowledgeArea knowledgeArea) {
        this.knowledgeArea = knowledgeArea;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public StandardizedCompetency getFirstVersion() {
        return firstVersion;
    }

    public void setFirstVersion(StandardizedCompetency firstVersion) {
        this.firstVersion = firstVersion;
    }

    public Set<StandardizedCompetency> getChildVersions() {
        return childVersions;
    }

    public void setChildVersions(Set<StandardizedCompetency> childVersions) {
        this.childVersions = childVersions;
    }

    public Set<Competency> getLinkedCompetencies() {
        return linkedCompetencies;
    }

    public void setLinkedCompetencies(Set<Competency> linkedCompetencies) {
        this.linkedCompetencies = linkedCompetencies;
    }
}
