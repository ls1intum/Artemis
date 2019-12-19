package de.tum.in.www1.artemis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "info.file-upload-exercise")
@Configuration("fileUploadExerciseProperties")
public class FileUploadExerciseProperties {

    private String[] filePatterns;

    public String[] getFilePatterns() {
        return filePatterns;
    }

    public void setFilePatterns(String[] filePatterns) {
        this.filePatterns = filePatterns;
    }
}
