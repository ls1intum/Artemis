import { WritableSignal } from '@angular/core';
import dayjs from 'dayjs/esm';

export type OperationName =
    | 'deleteOrphans'
    | 'deletePlagiarismComparisons'
    | 'deleteNonRatedResults'
    | 'deleteOldRatedResults'
    | 'deleteOldSubmissionVersions'
    | 'deleteOldFeedback'
    | 'warnOldCoursesReset'
    | 'resetOldCourses'
    | 'deleteOldCourseSubmissionVersions'
    | 'warnNotEnrolledUsers'
    | 'deleteNotEnrolledUsers'
    | 'deletePlagiarismCases';

/**
 * What an operation actually does to the affected entities. Drives the wording and the icon of the confirmation button
 * in both the table and the modal: "Delete" is wrong for an operation that only emails a warning, or that resets a
 * course's student data while keeping the course itself.
 */
export type CleanupAction = 'delete' | 'warn' | 'reset';

/** Instantiated in code; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CleanupOperation {
    name!: OperationName;
    action!: CleanupAction;
    // Optional (not `!`): clearing a picker sets these to undefined so validateDates can invalidate the row.
    deleteFrom: dayjs.Dayjs | undefined;
    deleteTo: dayjs.Dayjs | undefined;
    lastExecuted: dayjs.Dayjs | undefined;
    datesValid!: WritableSignal<boolean>;
    // Whether each picker's typed text currently parses. Kept separate from datesValid (the from<to range
    // check) so that unparseable input — which does not emit valueChange (keepInvalid) — still disables the
    // destructive Execute button. Default true; only the two dated pickers ever flip these.
    deleteFromValid!: WritableSignal<boolean>;
    deleteToValid!: WritableSignal<boolean>;
    // Age-based operations use configurable server-side cutoffs instead of an admin-picked date range, so they render
    // without date pickers (like deleteOrphans) and are always "valid".
    ageBased?: boolean;
}
