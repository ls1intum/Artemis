package de.tum.cit.aet.artemis.service.metis.conversation.errors;

import static de.tum.cit.aet.artemis.service.metis.conversation.ChannelService.CHANNEL_ENTITY_NAME;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.ErrorConstants;

public class ChannelNameDuplicateException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "channelNameDuplicate";

    public ChannelNameDuplicateException(String channelName) {
        super(ErrorConstants.CHANNEL_NAME_DUPLICATE, "Channel name already exists", CHANNEL_ENTITY_NAME, ERROR_KEY, getParameters(channelName));
    }

    private static Map<String, Object> getParameters(String channelName) {
        Map<String, String> params = new HashMap<>();
        params.put("channelName", channelName);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", "artemisApp.errors." + ERROR_KEY);
        parameters.put("params", params);
        return parameters;
    }

}
