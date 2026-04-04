import dayjs from 'dayjs/esm';

export type ExamExerciseStartPreparationStatus = {
    finished?: number;
    failed?: number;
    overall?: number;
    participationCount?: number;
    startedAt?: dayjs.Dayjs;
};
