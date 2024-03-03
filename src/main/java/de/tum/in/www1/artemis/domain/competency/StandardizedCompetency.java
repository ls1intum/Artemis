package de.tum.in.www1.artemis.domain.competency;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "standardized_competency")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class StandardizedCompetency extends BaseCompetency {

    @Column(name = "version")
    private String version;

    @ManyToOne
    @JoinColumn(name = "first_version_id")
    private StandardizedCompetency firstVersion;

    @ManyToOne
    @JoinColumn(name = "knowledge_area_id")
    private KnowledgeArea knowledgeArea;

    @ManyToOne
    @JoinColumn(name = "knowledge_area_id")
    private Source source;

    public StandardizedCompetency(String title, String description, CompetencyTaxonomy taxonomy, String version) {
        super(title, description, taxonomy);
        this.version = version;
    }

    public StandardizedCompetency() {

    }
}
