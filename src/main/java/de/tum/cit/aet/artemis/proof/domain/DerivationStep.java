package de.tum.cit.aet.artemis.proof.domain;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One step in a student's proof derivation.
 * Records which rule was applied, at which node (by index-path), and the full resulting expression tree.
 * Storing the full tree per step makes verification independent of the rule engine version.
 */
@Entity
@Table(name = "proof_derivation_step")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DerivationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    @JsonIgnore
    private ProofSubmission submission;

    @Column(name = "step_index")
    private int stepIndex;

    @Column(name = "applied_rule_id")
    private String appliedRuleId;

    @Convert(converter = IntegerListConverter.class)
    @Column(name = "target_node_path", columnDefinition = "longtext")
    private List<Integer> targetNodePath;

    @Convert(converter = MathNodeConverter.class)
    @Column(name = "result_expression", columnDefinition = "longtext")
    private MathNode resultExpression;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ProofSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(ProofSubmission submission) {
        this.submission = submission;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getAppliedRuleId() {
        return appliedRuleId;
    }

    public void setAppliedRuleId(String appliedRuleId) {
        this.appliedRuleId = appliedRuleId;
    }

    public List<Integer> getTargetNodePath() {
        return targetNodePath;
    }

    public void setTargetNodePath(List<Integer> targetNodePath) {
        this.targetNodePath = targetNodePath;
    }

    public MathNode getResultExpression() {
        return resultExpression;
    }

    public void setResultExpression(MathNode resultExpression) {
        this.resultExpression = resultExpression;
    }
}
