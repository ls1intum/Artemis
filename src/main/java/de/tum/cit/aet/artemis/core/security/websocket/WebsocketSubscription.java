package de.tum.cit.aet.artemis.core.security.websocket;

import java.security.Principal;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * A subscription request as seen by a {@link WebsocketTopicAccess.Custom custom} access check: who subscribes, to which destination, and the values of the template
 * variables.
 */
public final class WebsocketSubscription {

    /**
     * A canonical id: no sign, no leading zeros, at most 18 digits so that it always fits into a long.
     */
    private static final Pattern CANONICAL_ID = Pattern.compile("0|[1-9]\\d{0,17}");

    private final Principal subscriber;

    private final String destination;

    private final Map<String, String> variables;

    private final BooleanSupplier administratorAccess;

    WebsocketSubscription(Principal subscriber, String destination, Map<String, String> variables, BooleanSupplier administratorAccess) {
        this.subscriber = subscriber;
        this.destination = destination;
        this.variables = variables;
        this.administratorAccess = administratorAccess;
    }

    /**
     * @return the principal of the websocket session
     */
    public Principal subscriber() {
        return subscriber;
    }

    /**
     * @return the login of the subscriber
     */
    public String login() {
        return subscriber.getName();
    }

    /**
     * @return the destination of the subscription
     */
    public String destination() {
        return destination;
    }

    /**
     * Returns the value of a template variable that holds an id.
     *
     * @param name the variable name
     * @return the id
     * @throws IllegalArgumentException if the value is not a canonical decimal id, e.g. {@code 007}, {@code +7} or a number beyond the long range; the subscription is then
     *                                      rejected
     */
    public long id(String name) {
        String value = variable(name);
        if (!CANONICAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("The value of {" + name + "} in " + destination + " is not an id");
        }
        return Long.parseLong(value);
    }

    /**
     * Returns the value of a template variable.
     *
     * @param name the variable name
     * @return the value
     * @throws IllegalArgumentException if the template has no such variable
     */
    public String variable(String name) {
        String value = variables.get(name);
        if (value == null) {
            throw new IllegalArgumentException("The destination " + destination + " has no variable {" + name + "}");
        }
        return value;
    }

    /**
     * Whether the subscriber is an administrator with active elevation. Evaluated on demand, since it may need a database lookup.
     *
     * @return true for an elevated administrator
     */
    public boolean hasAdministratorAccess() {
        return administratorAccess.getAsBoolean();
    }
}
