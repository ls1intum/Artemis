package de.tum.in.www1.artemis.service.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StaticCodeAnalysisReportDTO {

    private StaticCodeAnalysisTool tool;

    private List<StaticCodeAnalysisIssue> issues = new ArrayList<>();

    public StaticCodeAnalysisTool getTool() {
        return tool;
    }

    public void setTool(StaticCodeAnalysisTool tool) {
        this.tool = tool;
    }

    public List<StaticCodeAnalysisIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<StaticCodeAnalysisIssue> issues) {
        this.issues = issues;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static final class StaticCodeAnalysisIssue {

        // Path to source file containing the error. Uses unix file separators
        private String filePath;

        private Integer startLine;

        private Integer endLine;

        private Integer startColumn;

        private Integer endColumn;

        private String rule;

        private String category;

        private String message;

        private String priority;

        private Double penalty;

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

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
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

        @Override
        public String toString() {
            return "StaticCodeAnalysisIssue{" + "filePath='" + filePath + '\'' + ", startLine=" + startLine + ", endLine=" + endLine + ", startColumn=" + startColumn
                    + ", endColumn=" + endColumn + ", rule='" + rule + '\'' + ", category='" + category + '\'' + ", message='" + message + '\'' + ", priority='" + priority + '\''
                    + '}';
        }
    }
}
