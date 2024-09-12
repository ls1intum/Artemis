package de.tum.cit.aet.artemis.programming.service.ci.notification.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: convert to record
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TestwiseCoverageReportDTO implements Serializable {

    @JsonProperty("uniformPath")
    private String uniformPath;

    @JsonProperty("duration")
    private double duration;

    @JsonProperty("content")
    private String content;

    @JsonProperty("paths")
    private List<CoveredPathsPerTestDTO> coveredPathsPerTestDTOs = new ArrayList<>();

    public String getUniformPath() {
        return uniformPath;
    }

    public void setUniformPath(String uniformPath) {
        this.uniformPath = uniformPath;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<CoveredPathsPerTestDTO> getCoveredPathsPerTestDTOs() {
        return coveredPathsPerTestDTOs;
    }

    public void setCoveredPathsPerTestDTOs(List<CoveredPathsPerTestDTO> coveredPathsPerTestDTOs) {
        this.coveredPathsPerTestDTOs = coveredPathsPerTestDTOs;
    }

    // TODO: convert to record
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static final class CoveredPathsPerTestDTO implements Serializable {

        @JsonProperty("path")
        private String path;

        @JsonProperty("files")
        private List<CoveredFilesPerTestDTO> coveredFilesPerTestDTOs = new ArrayList<>();

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<CoveredFilesPerTestDTO> getCoveredFilesPerTestDTOs() {
            return coveredFilesPerTestDTOs;
        }

        public void setCoveredFilesPerTestDTOs(List<CoveredFilesPerTestDTO> coveredFilesPerTestDTOs) {
            this.coveredFilesPerTestDTOs = coveredFilesPerTestDTOs;
        }
    }

    // TODO: convert to record
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static final class CoveredFilesPerTestDTO implements Serializable {

        @JsonProperty("fileName")
        private String fileName;

        @JsonProperty("coveredLines")
        private String coveredLinesWithRanges;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getCoveredLinesWithRanges() {
            return coveredLinesWithRanges;
        }

        public void setCoveredLinesWithRanges(String coveredLinesWithRanges) {
            this.coveredLinesWithRanges = coveredLinesWithRanges;
        }
    }
}
