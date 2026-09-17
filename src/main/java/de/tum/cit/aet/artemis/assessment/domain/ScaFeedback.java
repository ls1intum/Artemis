package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
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
 * SCA feedback is always negative; credits are {@code -penalty} (already capped per category when the row
 * is created). Rows of one result are found through the index on {@code result_id}, which also supports the
 * foreign key to {@code result}.
 */
@Entity
@Table(name = "sca_feedback")
public class ScaFeedback extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id")
    @JsonIgnore
    private Result result;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false)
    private StaticCodeAnalysisTool tool;

    /**
     * Column lengths of the schema (changelog 20260817090000); the writer truncates to these limits.
     */
    public static final int MAX_RULE_LENGTH = 255;

    public static final int MAX_FILE_PATH_LENGTH = 512;

    public static final int MAX_PRIORITY_LENGTH = 25;

    public static final int MAX_TOOL_CATEGORY_LENGTH = 50;

    /**
     * Matches the capacity of {@code static_code_analysis_category.name}, which this column stores.
     */
    public static final int MAX_CATEGORY_LENGTH = 255;

    @Column(name = "category", length = MAX_CATEGORY_LENGTH)
    private String category;

    @Column(name = "rule", length = MAX_RULE_LENGTH)
    private String rule;

    @Column(name = "file_path", length = MAX_FILE_PATH_LENGTH)
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "start_column")
    private Integer startColumn;

    @Column(name = "end_column")
    private Integer endColumn;

    @Column(name = "priority", length = MAX_PRIORITY_LENGTH)
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
     * The category of the issue as reported by the tool (e.g. {@code BAD_PRACTICE} from SpotBugs, or a
     * checkstyle rule group). Distinct from the Artemis grading category in {@link #category}, which the
     * categorization step derives from it: the legacy JSON exposed the tool category to the client, so it
     * is persisted and used for the synthesized {@code StaticCodeAnalysisIssue.category} field.
     */
    @Column(name = "tool_category", length = MAX_TOOL_CATEGORY_LENGTH)
    private String toolCategory;

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

    /**
     * Constant hash code — see {@link TestCaseFeedback#hashCode()}.
     */
    @Override
    public int hashCode() {
        return ScaFeedback.class.hashCode();
    }

    @Override
    public String toString() {
        return "ScaFeedback{id=" + getId() + ", tool=" + tool + ", rule='" + rule + "', filePath='" + filePath + "'}";
    }
}
