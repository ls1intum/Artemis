package de.tum.in.www1.artemis.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StaticCodeAnalysisReportDTO {

    private StaticCodeAnalysisTool tool;

    private List<StaticCodeAnalysisIssue> issues;

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
    public static final class StaticCodeAnalysisIssue {

        private String classname;

        private Integer line;

        private String type;

        private String priority;

        private String category;

        private String message;

        public String getClassname() {
            return classname;
        }

        public void setClassname(String classname) {
            this.classname = classname;
        }

        public Integer getLine() {
            return line;
        }

        public void setLine(Integer line) {
            this.line = line;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
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
    }
}
