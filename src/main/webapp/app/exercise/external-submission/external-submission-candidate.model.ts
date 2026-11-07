/**
 * Why a student cannot receive an external submission result, mirroring the server enum {@code ExternalSubmissionIneligibility}.
 */
export type ExternalSubmissionIneligibility = 'NOT_IN_COURSE' | 'NO_STUDENT_EXAM' | 'EXERCISE_NOT_IN_STUDENT_EXAM' | 'RESULT_ALREADY_EXISTS';

/**
 * A student suggested in the external submission dialog.
 */
export interface ExternalSubmissionCandidate {
    login: string;
    name?: string;
    /** Absent when the student can receive an external submission result. */
    ineligibility?: ExternalSubmissionIneligibility;
}
