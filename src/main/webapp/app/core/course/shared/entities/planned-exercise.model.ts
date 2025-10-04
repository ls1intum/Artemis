import dayjs, { Dayjs } from 'dayjs/esm';

export class PlannedExercise {
    id: number;
    title: string;
    releaseDate?: Dayjs;
    startDate?: Dayjs;
    dueDate?: Dayjs;
    assessmentDueDate?: Dayjs;

    constructor(rawPlannedExercise: RawPlannedExercise) {
        this.id = rawPlannedExercise.id;
        this.title = rawPlannedExercise.title;
        this.releaseDate = rawPlannedExercise.releaseDate ? dayjs(rawPlannedExercise.releaseDate) : undefined;
        this.startDate = rawPlannedExercise.startDate ? dayjs(rawPlannedExercise.startDate) : undefined;
        this.dueDate = rawPlannedExercise.dueDate ? dayjs(rawPlannedExercise.dueDate) : undefined;
        this.assessmentDueDate = rawPlannedExercise.assessmentDueDate ? dayjs(rawPlannedExercise.assessmentDueDate) : undefined;
    }
}

export class RawPlannedExercise {
    constructor(
        public id: number,
        public title: string,
        public releaseDate?: string,
        public startDate?: string,
        public dueDate?: string,
        public assessmentDueDate?: string,
    ) {}
}

export class PlannedExerciseCreateDTO {
    constructor(
        public title: string,
        public releaseDate?: Dayjs,
        public startDate?: Dayjs,
        public dueDate?: Dayjs,
        public assessmentDueDate?: Dayjs,
    ) {}
}
