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

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "knowledge_area")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class KnowledgeArea extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties("children")
    private KnowledgeArea parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("parent")
    private Set<KnowledgeArea> children = new HashSet<>();

    @OneToMany(mappedBy = "knowledgeArea", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("knowledgeArea")
    private Set<StandardizedCompetency> competencies = new HashSet<>();

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

    public KnowledgeArea getParent() {
        return parent;
    }

    public void setParent(KnowledgeArea parent) {
        this.parent = parent;
    }

    public Set<KnowledgeArea> getChildren() {
        return children;
    }

    public void setChildren(Set<KnowledgeArea> children) {
        this.children = children;
    }

    public Set<StandardizedCompetency> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(Set<StandardizedCompetency> competencies) {
        this.competencies = competencies;
    }
}
