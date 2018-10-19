package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A DragAndDropSubmittedAnswer.
 */
@Entity
@Table(name = "dnd_submitted_answer")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragAndDropSubmittedAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "submittedAnswer")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DragAndDropMapping> mappings = new HashSet<>();
    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Set<DragAndDropMapping> getMappings() {
        return mappings;
    }

    public DragAndDropSubmittedAnswer mappings(Set<DragAndDropMapping> dragAndDropMappings) {
        this.mappings = dragAndDropMappings;
        return this;
    }

    public DragAndDropSubmittedAnswer addMappings(DragAndDropMapping dragAndDropMapping) {
        this.mappings.add(dragAndDropMapping);
        dragAndDropMapping.setSubmittedAnswer(this);
        return this;
    }

    public DragAndDropSubmittedAnswer removeMappings(DragAndDropMapping dragAndDropMapping) {
        this.mappings.remove(dragAndDropMapping);
        dragAndDropMapping.setSubmittedAnswer(null);
        return this;
    }

    public void setMappings(Set<DragAndDropMapping> dragAndDropMappings) {
        this.mappings = dragAndDropMappings;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer = (DragAndDropSubmittedAnswer) o;
        if (dragAndDropSubmittedAnswer.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropSubmittedAnswer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropSubmittedAnswer{" +
            "id=" + getId() +
            "}";
    }
}
