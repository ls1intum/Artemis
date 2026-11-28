// SPDX-License-Identifier: Apache-2.0
// Adapted from the CodeAbility Sharing Plugin Platform API. See META-INF/NOTICE.
package de.tum.cit.aet.artemis.programming.service.sharing;

import java.util.List;

/**
 * Configuration advertised by a plugin to the Sharing Platform.
 *
 * @param version    configuration protocol version
 * @param pluginName plugin display name
 * @param actions    actions supported by the plugin
 * @author Michael Breu
 */
public record SharingPluginConfig(String version, String pluginName, List<Action> actions) {

    private static final String DEFAULT_VERSION = "0.1";

    public SharingPluginConfig {
        actions = actions == null ? null : actions.stream().toList();
    }

    /** Creates a configuration with the default protocol version. */
    public SharingPluginConfig(String pluginName, List<Action> actions) {
        this(DEFAULT_VERSION, pluginName, actions);
    }

    /**
     * A plugin action advertised to the Sharing Platform. The browser redirect
     * includes the basket token, the platform's {@code apiBaseURL} and a {@code returnURL}.
     *
     * @param actionId           plugin-specific action identifier
     * @param actionTransferURL  browser callback to which the platform adds the basket token and callback parameters
     * @param actionName         action name displayed to the user
     * @param filterELExpression Java Expression Language filter that selects exercises eligible for the action
     * @author Michael Breu
     */
    public record Action(String actionId, String actionTransferURL, String actionName, String filterELExpression) {
    }
}
