package de.tum.cit.aet.artemis.core.security.websocket;

/**
 * Declares the websocket topics of one module.
 * <p>
 * Each module that sends websocket messages has exactly one implementation, named {@code <Module>WebsocketTopics} and placed in the module's {@code web} package next to
 * its REST resources. It is a lazy Spring {@code @Component} carrying the same profile or {@code @Conditional} as the module, so the topics of a disabled module do not
 * exist. Its {@code public static final} {@link WebsocketTopic} and {@link WebsocketUserTopic} fields are the module's topics; {@link WebsocketTopicRegistry} discovers
 * them, and methods of the class implement the module's {@link WebsocketTopicAccess#custom custom} access checks.
 * <p>
 * Architecture tests require every topic constant to live in such a class.
 */
public interface WebsocketTopicProvider {
}
