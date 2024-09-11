package de.tum.cit.aet.artemis.domain.modeling;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "model_element")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelElement implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Size(min = 36, max = 36)
    @Column(name = "modelElementId", unique = true, columnDefinition = "CHAR(36)", length = 36)
    // Apollon Ids are exactly 36 characters long, Feedback.reference used this modelElementId to create a connection
    private String modelElementId;

    // TODO: we could also store the feedbackId here to simplify the check whether a model element has been corrected or not.

    @Column(name = "modelElementType", nullable = false)
    // TODO: we might want to use an enum here
    private String modelElementType;

    @ManyToOne
    @JsonIgnore
    private ModelingSubmission submission;

    @ManyToOne
    @JsonIgnore
    private ModelCluster cluster;

    public String getModelElementId() {
        return modelElementId;
    }

    public void setModelElementId(String modelElementId) {
        this.modelElementId = modelElementId;
    }

    public String getModelElementType() {
        return modelElementType;
    }

    public void setModelElementType(String modelElementType) {
        this.modelElementType = modelElementType;
    }

    public ModelingSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(ModelingSubmission submission) {
        this.submission = submission;
    }

    public ModelCluster getCluster() {
        return cluster;
    }

    public void setCluster(ModelCluster cluster) {
        this.cluster = cluster;
    }
}
