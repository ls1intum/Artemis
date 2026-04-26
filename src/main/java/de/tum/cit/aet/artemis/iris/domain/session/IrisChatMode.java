package de.tum.cit.aet.artemis.iris.domain.session;

/**
 * This enum represents the different types of Iris chat sessions.
 * Has to be in sync with the ChatServiceMode enum in the Artemis client & Pyris.
 */
public enum IrisChatMode {
    PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT, COURSE_CHAT, LECTURE_CHAT, TUTOR_SUGGESTION
}
