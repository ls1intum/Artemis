package de.tum.in.www1.artemis.domain.competency;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "standardized_competency")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class StandardizedCompetency extends BaseCompetency {

    @Column(name = "version")
    private String version;

    @ManyToOne
    @JoinColumn(name = "first_version_id")
    @JsonIgnoreProperties("childVersions")
    private StandardizedCompetency firstVersion;

    @OneToMany(mappedBy = "firstVersion", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("firstVersion")
    private Set<StandardizedCompetency> childVersions = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "knowledge_area_id")
    @JsonIgnoreProperties("competencies")
    private KnowledgeArea knowledgeArea;

    @ManyToOne
    @JoinColumn(name = "source_id")
    @JsonIgnoreProperties("competencies")
    private Source source;

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

    public StandardizedCompetency getFirstVersion() {
        return firstVersion;
    }

    public void setFirstVersion(StandardizedCompetency firstVersion) {
        this.firstVersion = firstVersion;
    }

    public KnowledgeArea getKnowledgeArea() {
        return knowledgeArea;
    }

    public Set<StandardizedCompetency> getChildVersions() {
        return childVersions;
    }

    public void setChildVersions(Set<StandardizedCompetency> childVersions) {
        this.childVersions = childVersions;
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
}
