package de.tum.cit.aet.artemis.domain.competency;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.DomainObject;

/**
 * BaseCompetency is an abstract class that contains basic information shared between all competency types.
 * It is extended by {@link CourseCompetency} and {@link StandardizedCompetency}
 */
@MappedSuperclass
public abstract class BaseCompetency extends DomainObject {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    /**
     * The type of competency according to Bloom's revised taxonomy.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Bloom%27s_taxonomy">Wikipedia</a>
     */
    @Column(name = "taxonomy")
    @Convert(converter = CompetencyTaxonomy.TaxonomyConverter.class)
    @JsonInclude
    private CompetencyTaxonomy taxonomy;

    public BaseCompetency(String title, String description, CompetencyTaxonomy taxonomy) {
        this.title = title;
        this.description = description;
        this.taxonomy = taxonomy;
    }

    public BaseCompetency() {

    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CompetencyTaxonomy getTaxonomy() {
        return taxonomy;
    }

    public void setTaxonomy(CompetencyTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }
}
