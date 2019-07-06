package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A TextBlock.
 */
@Entity
@Table(name = "text_block")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class TextBlock implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text")
    private String text;

    @ManyToOne
    @JsonIgnoreProperties("blocks")
    private TextSubmission submission;

    @ManyToOne
    @JsonIgnoreProperties("blocks")
    private TextCluster cluster;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public TextBlock text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public TextSubmission getSubmission() {
        return submission;
    }

    public TextBlock submission(TextSubmission textSubmission) {
        this.submission = textSubmission;
        return this;
    }

    public void setSubmission(TextSubmission textSubmission) {
        this.submission = textSubmission;
    }

    public TextCluster getCluster() {
        return cluster;
    }

    public TextBlock cluster(TextCluster textCluster) {
        this.cluster = textCluster;
        return this;
    }

    public void setCluster(TextCluster textCluster) {
        this.cluster = textCluster;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextBlock)) {
            return false;
        }
        return id != null && id.equals(((TextBlock) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TextBlock{" + "id=" + getId() + ", text='" + getText() + "'" + "}";
    }
}
