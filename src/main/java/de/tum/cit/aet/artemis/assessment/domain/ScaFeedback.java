package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;

/**
 * One static-code-analysis issue of one {@link Result}, stored with structured columns.
 * <p>
 * Successor of the former SCA rows in the {@code feedback} table, which serialized a whole
 * {@code StaticCodeAnalysisIssue} as JSON into {@code detail_text}. Splitting the positional fields
 * (file, lines, columns) into columns makes the rule message deduplicable via {@link FeedbackMessage}
 * (on production data, 2.5M SCA JSON blobs contain only ~105k distinct messages), and the category
 * penalty can be updated without rewriting a JSON blob.
 * <p>
 * The primary key is {@code (result_id, seq)} — see {@link FeedbackItemId}. SCA feedback is always
 * negative; credits are {@code -penalty} (already capped per category when the row is created).
 */
@Entity
@Table(name = "sca_feedback")
public class ScaFeedback {

    @EmbeddedId
    private FeedbackItemId id = new FeedbackItemId();

    @MapsId("resultId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id")
    @JsonIgnore
    private Result result;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false)
    private StaticCodeAnalysisTool tool;

    @Column(name = "category")
    private String category;

    @Column(name = "rule")
    private String rule;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "start_column")
    private Integer startColumn;

    @Column(name = "end_column")
    private Integer endColumn;

    @Column(name = "priority")
    private String priority;

    @Column(name = "penalty")
    private Double penalty;

    /**
     * See {@link TestCaseFeedback#getMessage()}: no database foreign key on purpose.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JsonIgnore
    private FeedbackMessage message;

    /**
     * The tool-reported category of the issue (e.g. a checkstyle rule group). Only needed transiently
     * between report parsing and the categorization step, which maps it to the Artemis
     * {@code StaticCodeAnalysisCategory} stored in {@link #category}. Never persisted.
     */
    @Transient
    @JsonIgnore
    private String toolCategory;

    public FeedbackItemId getId() {
        return id;
    }

    public void setId(FeedbackItemId id) {
        this.id = id;
    }

    public int getSeq() {
        return id.getSeq();
    }

    public void setSeq(int seq) {
        id.setSeq(seq);
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public StaticCodeAnalysisTool getTool() {
        return tool;
    }

    public void setTool(StaticCodeAnalysisTool tool) {
        this.tool = tool;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public void setEndLine(Integer endLine) {
        this.endLine = endLine;
    }

    public Integer getStartColumn() {
        return startColumn;
    }

    public void setStartColumn(Integer startColumn) {
        this.startColumn = startColumn;
    }

    public Integer getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(Integer endColumn) {
        this.endColumn = endColumn;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Double getPenalty() {
        return penalty;
    }

    public void setPenalty(Double penalty) {
        this.penalty = penalty;
    }

    public FeedbackMessage getMessage() {
        return message;
    }

    public void setMessage(FeedbackMessage message) {
        this.message = message;
    }

    public String getToolCategory() {
        return toolCategory;
    }

    public void setToolCategory(String toolCategory) {
        this.toolCategory = toolCategory;
    }

    /**
     * @return the deduplicated message text, or {@code null}. May trigger lazy initialization.
     */
    @JsonIgnore
    public String getMessageText() {
        return message == null ? null : message.getText();
    }

    /**
     * @return the credits of this issue: {@code -penalty}, or 0 if no penalty applies (e.g. the category
     *         is not graded).
     */
    @JsonIgnore
    public double getCredits() {
        return penalty == null ? 0.0 : -penalty;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScaFeedback otherFeedback)) {
            return false;
        }
        if (id == null || id.getResultId() == null || otherFeedback.id == null || otherFeedback.id.getResultId() == null) {
            return false;
        }
        return id.equals(otherFeedback.id);
    }

    /**
     * Constant hash code — see {@link TestCaseFeedback#hashCode()}.
     */
    @Override
    public int hashCode() {
        return ScaFeedback.class.hashCode();
    }

    @Override
    public String toString() {
        return "ScaFeedback{id=" + id + ", tool=" + tool + ", rule='" + rule + "', filePath='" + filePath + "'}";
    }
}
