import dayjs from 'dayjs/esm';

export type OperationName =
    | 'deleteOrphans'
    | 'deletePlagiarismComparisons'
    | 'deleteNonRatedResults'
    | 'deleteOldRatedResults'
    | 'deleteOldSubmissionVersions'
    | 'deleteOldFeedback';

export interface CleanupOperation {
    name: OperationName;
    deleteFrom: dayjs.Dayjs;
    deleteTo: dayjs.Dayjs;
    lastExecuted: dayjs.Dayjs;
}
