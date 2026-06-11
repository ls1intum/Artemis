package de.tum.cit.aet.artemis.iris.domain.session;

/**
 * The chat mode of an {@link IrisChatSession}, persisted in the {@code iris_session.chat_mode} column.
 * <p>
 * {@link IrisTutorSuggestionSession} is a separate top-level session type (discriminator='TUTOR_SUGGESTION')
 * and intentionally has no chat mode — neither in the database nor in the Java domain model.
 * <p>
 * Has to be in sync with the chat-mode values in the Artemis client &amp; Pyris.
 */
public enum IrisChatMode {
    PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT, COURSE_CHAT, LECTURE_CHAT
}
