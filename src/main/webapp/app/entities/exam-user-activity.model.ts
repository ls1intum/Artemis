import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';

export enum ExamActionType {
    STARTED_EXAM = 'STARTED_EXAM',
    ENDED_EXAM = 'ENDED_EXAM',
    HANDED_IN_EARLY = 'HANDED_IN_EARLY',
    CONTINUED_AFTER_HAND_IN_EARLY = 'CONTINUED_AFTER_HAND_IN_EARLY',
    SWITCHED_EXERCISE = 'SWITCHED_EXERCISE',
    SAVED_EXERCISE = 'SAVED_EXERCISE',
    CONNECTION_UPDATED = 'CONNECTION_UPDATED',
}

export class ExamActivity {
    public examActions: ExamAction[];

    constructor() {
        this.examActions = [];
    }

    public addAction(examUserAction: ExamAction) {
        this.examActions.push(examUserAction);
    }
}

export class ExamAction implements BaseEntity {
    public id?: number;
    public timestamp?: dayjs.Dayjs;
    public actionDetail?: ExamActionDetail;

    constructor(timestamp: dayjs.Dayjs, actionDetail: ExamActionDetail) {
        this.timestamp = timestamp;
        this.actionDetail = actionDetail;
    }
}

export abstract class ExamActionDetail {
    public readonly examActionEvent: ExamActionType;

    protected constructor(examActionEvent: ExamActionType) {
        this.examActionEvent = examActionEvent;
    }
}

export class StartedExamActionDetail extends ExamActionDetail {
    public sessionId?: number;

    constructor(sessionId: number | undefined) {
        super(ExamActionType.STARTED_EXAM);
        this.sessionId = sessionId;
    }
}

export class EndedExamActionDetail extends ExamActionDetail {
    constructor() {
        super(ExamActionType.ENDED_EXAM);
    }
}

export class HandedInEarlyActionDetail extends ExamActionDetail {
    constructor() {
        super(ExamActionType.HANDED_IN_EARLY);
    }
}

export class ContinueAfterHandedInEarlyActionDetail extends ExamActionDetail {
    constructor() {
        super(ExamActionType.CONTINUED_AFTER_HAND_IN_EARLY);
    }
}

export class SwitchedExerciseActionDetail extends ExamActionDetail {
    public exerciseId?: number;

    constructor(exerciseId: number | undefined) {
        super(ExamActionType.SWITCHED_EXERCISE);
        this.exerciseId = exerciseId;
    }
}

export class SavedSubmissionActionDetail extends ExamActionDetail {
    public forced?: boolean;
    public submissionId?: number;
    public failed?: boolean;
    public automatically?: boolean;

    constructor(forced: boolean, submissionId: number | undefined, failed: boolean, automatically: boolean) {
        super(ExamActionType.SAVED_EXERCISE);
        this.forced = forced;
        this.submissionId = submissionId;
        this.failed = failed;
        this.automatically = automatically;
    }
}

export class ConnectionUpdatedActionDetail extends ExamActionDetail {
    public connected?: boolean;

    constructor(connected: boolean) {
        super(ExamActionType.CONNECTION_UPDATED);
        this.connected = connected;
    }
}
