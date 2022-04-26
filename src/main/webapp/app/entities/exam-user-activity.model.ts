import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';

export enum ExamUserActionType {
    STARTED_EXAM = 'STARTED_EXAM',
    ENDED_EXAM = 'ENDED_EXAM',
    HANDED_IN_EARLY = 'HANDED_IN_EARLY',
    SWITCHED_EXERCISE = 'SWITCHED_EXERCISE',
    SAVED_EXERCISE = 'SAVED_EXERCISE',
}

export class ExamUserActivity implements BaseEntity {
    public id?: number;
    public examUserActions?: ExamUserAction[];

    constructor(id: number, examUserActions: ExamUserAction[]) {
        this.id = id;
        this.examUserActions = examUserActions;
    }
}

class ExamUserAction implements BaseEntity {
    public id?: number;
    public manually?: boolean;
    public timestamp?: dayjs.Dayjs;
    public actionDetail?: ExamUserActionDetail;

    constructor(id: number, manually: boolean, timestamp: dayjs.Dayjs, actionDetail: ExamUserActionDetail) {
        this.id = id;
        this.manually = manually;
        this.timestamp = timestamp;
        this.actionDetail = actionDetail;
    }
}

abstract class ExamUserActionDetail {
    public readonly examUserActionType: ExamUserActionType;

    protected constructor(examUserActionType: ExamUserActionType) {
        this.examUserActionType = examUserActionType;
    }
}

export class ExamUserStartedExamActionDetail extends ExamUserActionDetail {
    constructor() {
        super(ExamUserActionType.STARTED_EXAM);
    }
}

export class ExamUserEndedExamActionDetail extends ExamUserActionDetail {
    constructor() {
        super(ExamUserActionType.ENDED_EXAM);
    }
}

export class ExamUserHandedInEarlyActionDetail extends ExamUserActionDetail {
    constructor() {
        super(ExamUserActionType.HANDED_IN_EARLY);
    }
}

export class ExamUserSwitchedExerciseActionDetail extends ExamUserActionDetail {
    public from?: number;
    public to?: number;

    constructor(from: number, to: number) {
        super(ExamUserActionType.SWITCHED_EXERCISE);
        this.from = from;
        this.to = to;
    }
}

export class ExamUserSavedExerciseActionDetail extends ExamUserActionDetail {
    public forced?: boolean;
    public submissionId?: number;

    constructor(forced: boolean, submissionId: number) {
        super(ExamUserActionType.SAVED_EXERCISE);
        this.forced = forced;
        this.submissionId = submissionId;
    }
}
