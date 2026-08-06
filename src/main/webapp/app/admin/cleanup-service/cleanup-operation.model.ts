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

/** Instantiated in code; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CleanupOperation {
    name!: OperationName;
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
