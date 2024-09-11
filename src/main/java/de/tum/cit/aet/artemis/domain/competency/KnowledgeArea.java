package de.tum.cit.aet.artemis.domain.competency;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.domain.DomainObject;

@Entity
@Table(name = "knowledge_area")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class KnowledgeArea extends DomainObject {

    @JsonIgnore
    public static final int MAX_TITLE_LENGTH = 255;

    @JsonIgnore
    public static final int MAX_SHORT_TITLE_LENGTH = 10;

    @JsonIgnore
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "short_title", nullable = false)
    private String shortTitle;

    @Column(name = "description")
    private String description;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({ "parent", "children" })
    private KnowledgeArea parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({ "parent" })
    private Set<KnowledgeArea> children = new HashSet<>();

    @OneToMany(mappedBy = "knowledgeArea", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("knowledgeArea")
    private Set<StandardizedCompetency> competencies = new HashSet<>();

    public KnowledgeArea() {

    }

    public KnowledgeArea(String title, String shortTitle, String description) {
        this.title = title;
        this.shortTitle = shortTitle;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortTitle() {
        return shortTitle;
    }

    public void setShortTitle(String shortTitle) {
        this.shortTitle = shortTitle;
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

    public void addToChildren(KnowledgeArea knowledgeArea) {
        this.children.add(knowledgeArea);
    }
}
