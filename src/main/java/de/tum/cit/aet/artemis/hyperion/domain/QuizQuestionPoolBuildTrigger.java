package de.tum.cit.aet.artemis.hyperion.domain;

/**
 * What started a quiz question pool build.
 */
public enum QuizQuestionPoolBuildTrigger {
    /** An instructor pressed "build" on the pool page. */
    MANUAL,
    /** The nightly schedule built the pool of a course that opted in. */
    SCHEDULED
}
