package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "standardized_competency")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class StandardizedCompetency extends BaseCompetency {

    @JsonIgnore
    public static final int MAX_TITLE_LENGTH = 255;

    @JsonIgnore
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    @JsonIgnore
    public static final int MAX_VERSION_LENGTH = 30;

    @JsonIgnore
    public static final String FIRST_VERSION = "1.0.0";

    @Column(name = "version", nullable = false)
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
    private Set<CourseCompetency> linkedCompetencies = new HashSet<>();

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

    public Set<CourseCompetency> getLinkedCompetencies() {
        return linkedCompetencies;
    }

    public void setLinkedCompetencies(Set<CourseCompetency> linkedCompetencies) {
        this.linkedCompetencies = linkedCompetencies;
    }
}
