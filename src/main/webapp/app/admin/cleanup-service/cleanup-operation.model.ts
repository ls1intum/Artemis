import { WritableSignal } from '@angular/core';
import dayjs from 'dayjs/esm';

export type OperationName =
    'deleteOrphans' | 'deletePlagiarismComparisons' | 'deleteNonRatedResults' | 'deleteOldRatedResults' | 'deleteOldSubmissionVersions' | 'deleteOldFeedback';

/** Instantiated in code; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CleanupOperation {
    name!: OperationName;
    // Optional (not `!`): clearing a picker sets these to undefined so validateDates can invalidate the row.
    deleteFrom: dayjs.Dayjs | undefined;
    deleteTo: dayjs.Dayjs | undefined;
    lastExecuted: dayjs.Dayjs | undefined;
    datesValid!: WritableSignal<boolean>;
}
