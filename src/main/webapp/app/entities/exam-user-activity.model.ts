import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';

/**
 * Defines the available actions a student can perform.
 */
export enum ExamActionType {
    STARTED_EXAM = 'STARTED_EXAM',
    ENDED_EXAM = 'ENDED_EXAM',
    HANDED_IN_EARLY = 'HANDED_IN_EARLY',
    CONTINUED_AFTER_HAND_IN_EARLY = 'CONTINUED_AFTER_HAND_IN_EARLY',
    SWITCHED_EXERCISE = 'SWITCHED_EXERCISE',
    SAVED_EXERCISE = 'SAVED_EXERCISE',
    CONNECTION_UPDATED = 'CONNECTION_UPDATED',
}

/**
 * A container/wrapper for the exam actions.
 */
export class ExamActivity {
    public examActions: ExamAction[];
    public id?: number;

    constructor() {
        this.examActions = [];
    }

    public addAction(examUserAction: ExamAction) {
        this.examActions.push(examUserAction);
    }
}

/**
 * Each action has some common values: id, timestamp and type.
 */
export abstract class ExamAction implements BaseEntity {
    public id?: number;
    public studentExamId?: number;
    public examActivityId?: number;
    public timestamp?: dayjs.Dayjs;
    public ceiledTimestamp?: dayjs.Dayjs;
    public readonly type: ExamActionType;

    protected constructor(examActionEvent: ExamActionType) {
        this.type = examActionEvent;
    }
}

/**
 * This action indicates whether a student started or restarted the exam.
 */
export class StartedExamAction extends ExamAction {
    public sessionId?: number;

    constructor(sessionId: number | undefined) {
        super(ExamActionType.STARTED_EXAM);
        this.sessionId = sessionId;
    }
}

/**
 * This action indicates when a student has ended his exam.
 */
export class EndedExamAction extends ExamAction {
    constructor() {
        super(ExamActionType.ENDED_EXAM);
    }
}

/**
 * This action indicates whether a student wants to hand in early or not.
 */
export class HandedInEarlyAction extends ExamAction {
    constructor() {
        super(ExamActionType.HANDED_IN_EARLY);
    }
}

/**
 * This action indicates whether a student has continued after visiting the handed in early page or not.
 */
export class ContinuedAfterHandedInEarlyAction extends ExamAction {
    constructor() {
        super(ExamActionType.CONTINUED_AFTER_HAND_IN_EARLY);
    }
}

/**
 * This action indicates whether a student switched to another exercise or to the overview page.
 */
export class SwitchedExerciseAction extends ExamAction {
    public exerciseId?: number;

    constructor(exerciseId: number | undefined) {
        super(ExamActionType.SWITCHED_EXERCISE);
        this.exerciseId = exerciseId;
    }
}

/**
 * This action indicates whether a student saved an exercise manually or automatically.
 */
export class SavedExerciseAction extends ExamAction {
    public forced?: boolean;
    public submissionId?: number;
    public exerciseId?: number;
    public failed?: boolean;
    public automatically?: boolean;

    constructor(forced: boolean, submissionId: number | undefined, exerciseId: number | undefined, failed: boolean, automatically: boolean) {
        super(ExamActionType.SAVED_EXERCISE);
        this.forced = forced;
        this.submissionId = submissionId;
        this.exerciseId = exerciseId;
        this.failed = failed;
        this.automatically = automatically;
    }
}

/**
 * This action shows whether a student has a connection update during the exam or not.
 */
export class ConnectionUpdatedAction extends ExamAction {
    public connected?: boolean;

    constructor(connected: boolean) {
        super(ExamActionType.CONNECTION_UPDATED);
        this.connected = connected;
    }
}
