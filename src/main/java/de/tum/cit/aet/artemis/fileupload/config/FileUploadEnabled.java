package de.tum.cit.aet.artemis.fileupload.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the FileUpload Exercise module is enabled.
 * Based on this condition, Spring components concerning atlas functionality can be enabled or disabled.
 */
public class FileUploadEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public FileUploadEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isFileuploadExerciseEnabled(context.getEnvironment());
    }
}
