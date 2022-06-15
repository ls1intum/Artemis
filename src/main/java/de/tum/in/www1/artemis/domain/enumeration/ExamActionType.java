package de.tum.in.www1.artemis.domain.enumeration;

/**
 * This enumeration defines the possible actions that are performed during an exam.
 * It should contain the same values as in "exam-user-activity.model.ts".
 */
public enum ExamActionType {

    /*
     * Defines the event when a student starts or restarts an exam.
     */
    STARTED_EXAM,
    /*
     * Defines the event when a student ends the exam.
     */
    ENDED_EXAM,
    /*
     * Defines the event when a student visit the handed in early page (does not have to mean that he actually submits early).
     */
    HANDED_IN_EARLY,
    /*
     * Defines the event when a student resumes the work after visiting the handed in early page.
     */
    CONTINUED_AFTER_HAND_IN_EARLY,
    /*
     * Defines the event when a student switches between exercises or the overview page.
     */
    SWITCHED_EXERCISE,
    /*
     * Defines the event when submissions are saved (automatically and manually)
     */
    SAVED_EXERCISE,
    /*
     * Defines the event when the student connection is updated.
     */
    CONNECTION_UPDATED,

}
