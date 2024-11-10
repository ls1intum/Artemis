import { WritableSignal } from '@angular/core';
import dayjs from 'dayjs/esm';

export type OperationName =
    | 'deleteOrphans'
    | 'deletePlagiarismComparisons'
    | 'deleteNonRatedResults'
    | 'deleteOldRatedResults'
    | 'deleteOldSubmissionVersions'
    | 'deleteOldFeedback';

export class CleanupOperation {
    name: OperationName;
    deleteFrom: dayjs.Dayjs;
    deleteTo: dayjs.Dayjs;
    lastExecuted: dayjs.Dayjs | undefined;
    datesValid: WritableSignal<boolean>;
}
