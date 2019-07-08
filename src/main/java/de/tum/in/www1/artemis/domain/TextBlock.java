package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.apache.commons.codec.digest.DigestUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A TextBlock.
 */
@Entity
@Table(name = "text_block")
public class TextBlock implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Size(min = 40, max = 40)
    @Column(name = "id", unique = true, columnDefinition = "CHAR(40)")
    private String id;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "startIndex", nullable = false)
    private int startIndex;

    @Column(name = "endIndex", nullable = false)
    private int endIndex;

    @ManyToOne
    @JsonIgnoreProperties("blocks")
    private TextSubmission submission;

    @ManyToOne
    @JsonIgnore
    private TextCluster cluster;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void computeId() {
        final String idString = submission.getId() + ";" + startIndex + "-" + endIndex + ";" + text;
        id = DigestUtils.sha1Hex(idString);
    }

    public int getStartIndex() {
        return startIndex;
    }

    public TextBlock startIndex(int startIndex) {
        this.startIndex = startIndex;
        return this;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public TextBlock endIndex(int endIndex) {
        this.endIndex = endIndex;
        return this;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
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
