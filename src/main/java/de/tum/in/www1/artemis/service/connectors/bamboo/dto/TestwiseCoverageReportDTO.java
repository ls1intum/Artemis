package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestwiseCoverageReportDTO {

    @JsonProperty("uniformPath")
    private String uniformPath;

    @JsonProperty("duration")
    private double duration;

    @JsonProperty("content")
    private String content;

    @JsonProperty("paths")
    private List<CoveredPathsPerTestDTO> coveredPathsPerTestDTOS = new ArrayList<>();

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

    public List<CoveredPathsPerTestDTO> getCoveredPathsPerTestDTOS() {
        return coveredPathsPerTestDTOS;
    }

    public void setCoveredPathsPerTestDTOS(List<CoveredPathsPerTestDTO> coveredPathsPerTestDTOS) {
        this.coveredPathsPerTestDTOS = coveredPathsPerTestDTOS;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class CoveredPathsPerTestDTO {

        @JsonProperty("path")
        private String path;

        @JsonProperty("files")
        private List<CoveredFilesPerTestDTO> coveredFilesPerTestDTOS = new ArrayList<>();

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<CoveredFilesPerTestDTO> getCoveredFilesPerTestDTOS() {
            return coveredFilesPerTestDTOS;
        }

        public void setCoveredFilesPerTestDTOS(List<CoveredFilesPerTestDTO> coveredFilesPerTestDTOS) {
            this.coveredFilesPerTestDTOS = coveredFilesPerTestDTOS;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class CoveredFilesPerTestDTO {

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
