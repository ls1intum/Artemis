package de.tum.in.www1.artemis.domain;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A TextBlock.
 */
@Entity
@Table(name = "text_block")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextBlock implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Size(min = 40, max = 40)
    @Column(name = "id", unique = true, columnDefinition = "CHAR(40)", length = 40)
    private String id;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "start_index", nullable = false)
    private int startIndex;

    @Column(name = "end_index", nullable = false)
    private int endIndex;

    @Transient
    @JsonSerialize
    private int numberOfAffectedSubmissions;

    /**
     * Indicate if block was generated by Segmentation Algorithm or by Assessor
     * Manual Blocks can be deleted, if not associated with feedback.
     * Automatic Blocks should be persisted regardless.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TextBlockType type = TextBlockType.MANUAL;

    @SuppressWarnings("FieldCanBeLocal")
    @Column(name = "position_in_cluster")
    private Integer positionInCluster = null;

    @Column(name = "added_distance")
    @JsonIgnore
    private Double addedDistance;

    // There is a foreign key on delete set null
    @OneToOne
    @JsonIgnore
    private Feedback feedback;

    @ManyToOne
    @JsonIgnore
    private TextSubmission submission;

    @ManyToOne
    @JsonIgnore
    private TextAssessmentKnowledge knowledge;

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

    /**
     * Identical with src/main/webapp/app/entities/text-block/text-block.model.ts
     */
    public void computeId() {
        final long submissionId = submission != null ? submission.getId() : 0;
        final String idString = submissionId + ";" + startIndex + "-" + endIndex + ";" + text;
        id = sha1Hex(idString);
    }

    public TextBlock id(String id) {
        setId(id);
        return this;
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

    public void setTextFromSubmission() {
        final String submissionText = submission.getText();
        final String blockText = submissionText.substring(startIndex, endIndex);
        setText(blockText);
    }

    public TextBlockType getType() {
        return type;
    }

    public TextBlock automatic() {
        this.type = TextBlockType.AUTOMATIC;
        return this;
    }

    public TextBlock manual() {
        this.type = TextBlockType.MANUAL;
        return this;
    }

    public TextSubmission getSubmission() {
        return submission;
    }

    public TextBlock submission(TextSubmission textSubmission) {
        this.submission = textSubmission;
        return this;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
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

    void setPositionInCluster(Integer positionInCluster) {
        this.positionInCluster = positionInCluster;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TextBlock)) {
            return false;
        }
        return id != null && id.equals(((TextBlock) obj).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TextBlock{" + "id=" + getId() + ", text='" + getText() + "'" + ", startIndex='" + getStartIndex() + "'" + ", endIndex='" + getEndIndex() + "'" + ", type='"
                + getType() + "'" + "}";
    }

    @JsonIgnore
    public boolean isAssessable() {
        return submission.getLatestResult() != null;
    }

    public void setAddedDistance(double addedDistance) {
        this.addedDistance = addedDistance;
    }

    public Double getAddedDistance() {
        return addedDistance;
    }

    public int getNumberOfAffectedSubmissions() {
        return numberOfAffectedSubmissions;
    }

    public void setNumberOfAffectedSubmissions(int numberOfAffectedSubmissions) {
        this.numberOfAffectedSubmissions = numberOfAffectedSubmissions;
    }

    public TextAssessmentKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(TextAssessmentKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
