package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.in.www1.artemis.domain.enumeration.StaticAssessmentTool;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StaticAssessmentReportDTO {

    private StaticAssessmentTool tool;

    private List<BambooStaticAssessmentIssue> issues;

    public StaticAssessmentTool getTool() {
        return tool;
    }

    public void setTool(StaticAssessmentTool tool) {
        this.tool = tool;
    }

    public List<BambooStaticAssessmentIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<BambooStaticAssessmentIssue> issues) {
        this.issues = issues;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooStaticAssessmentIssue {

        private String classname;

        private int line;

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

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
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
