package de.tum.cit.aet.artemis.fileupload.config;

import static de.tum.cit.aet.artemis.core.config.Constants.FILEUPLOAD_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired API is not present.
 */
public class FileUploadApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public FileUploadApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, FILEUPLOAD_ENABLED_PROPERTY_NAME);
    }
}
