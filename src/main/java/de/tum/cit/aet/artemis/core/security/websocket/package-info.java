/**
 * Declaration and authorization of websocket topics.
 * <p>
 * Every topic the server publishes to is declared once, as a {@link de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic} or a
 * {@link de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic} constant in the {@code *WebsocketTopics} class of the module that owns it. A broadcast topic
 * carries the {@link de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess} rule that decides who may subscribe, in the same way a REST endpoint carries its
 * {@code @Enforce...} annotation. Messages can only be sent to declared topics, and a subscription to a destination that no topic declares is rejected.
 * <p>
 * See {@code documentation/docs/developer/guidelines/websocket.mdx} for the full guideline.
 */
package de.tum.cit.aet.artemis.core.security.websocket;
