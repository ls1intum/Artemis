package de.tum.cit.aet.artemis.iris.domain.askuser;

/**
 * The events that can trigger a run of the Iris ask-user-mode pipeline, sent by the client to inform Pyris about the current state of the quiz interaction.
 */
public enum IrisPipeEvent {
    BUILD_WITH_POINTS, USER_STARTS_QUIZ, FIRST_QUESTION, NEXT_QUESTION, QUIZ_FINISHED, TIMER_RAN_OUT, TAB_DEFOCUS
}
