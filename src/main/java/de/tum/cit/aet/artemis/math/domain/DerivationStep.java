package de.tum.cit.aet.artemis.math.domain;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

/**
 * One step in a student's math derivation.
 * Records which rule was applied, at which node (by index-path), and the full resulting expression tree.
 * Storing the full tree per step makes verification independent of the rule engine version.
 */
@Getter
@Entity
@Table(name = "math_derivation_step")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DerivationStep {

    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    @JsonIgnore
    private MathSubmission submission;

    @Setter
    @Column(name = "step_index")
    private int stepIndex;

    @Setter
    @Column(name = "applied_rule_id")
    private String appliedRuleId;

    @Setter
    @Convert(converter = IntegerListConverter.class)
    @Column(name = "target_node_path", columnDefinition = "longtext")
    private List<Integer> targetNodePath;

    @Setter
    @Convert(converter = MathNodeConverter.class)
    @Column(name = "result_expression", columnDefinition = "longtext")
    private MathNode resultExpression;

    /**
     * Direction in which the rule was applied. {@link StepDirection#FORWARD} (default) replays
     * the rule as {@code pattern → template}; {@link StepDirection#REVERSE} replays it as
     * {@code template → pattern} and is only valid when {@link RewriteRule#direction()} is
     * {@link RuleDirection#BIDIRECTIONAL}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 8, nullable = false)
    private StepDirection direction = StepDirection.FORWARD;

    public void setDirection(StepDirection direction) {
        this.direction = direction == null ? StepDirection.FORWARD : direction;
    }
}
