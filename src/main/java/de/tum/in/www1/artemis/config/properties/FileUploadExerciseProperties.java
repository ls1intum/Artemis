package de.tum.in.www1.artemis.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "info.file-upload-exercise")
@Configuration
public class FileUploadExerciseProperties {

    private List<String> filePatterns;

    public List<String> getFilePatterns() {
        return filePatterns;
    }

    public void setFilePatterns(List<String> filePatterns) {
        this.filePatterns = filePatterns;
    }
}
