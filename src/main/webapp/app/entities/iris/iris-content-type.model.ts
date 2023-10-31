export enum IrisMessageContentType {
    TEXT = 'text',
    EXERCISE_PLAN = 'exercise_plan',
}

export abstract class IrisMessageContent {
    id?: number;
    messageId?: number;

    protected constructor(public type: IrisMessageContentType) {}
}

export class IrisTextMessageContent extends IrisMessageContent {
    constructor(public textContent: string) {
        super(IrisMessageContentType.TEXT);
    }
}

export class IrisExercisePlan extends IrisMessageContent {
    executing: boolean; // Client-side only

    constructor(public steps: IrisExercisePlanStep[]) {
        super(IrisMessageContentType.EXERCISE_PLAN);
    }
}

export class IrisExercisePlanStep {
    id: number;
    plan: number;
    component: ExerciseComponent;
    instructions: string;
    executed: boolean;
    executionStage?: ExecutionStage; // Client-side only
    hidden?: boolean; // Client-side only
}

export function getExecutionStage(step: IrisExercisePlanStep): ExecutionStage {
    if (!step.executionStage) {
        // If this is the first time we're checking the execution stage, use the executed flag
        step.executionStage = step.executed ? ExecutionStage.COMPLETE : ExecutionStage.NOT_EXECUTED;
    }
    return step.executionStage;
}

export function isNotExecuted(step: IrisExercisePlanStep): boolean {
    return getExecutionStage(step) === ExecutionStage.NOT_EXECUTED;
}

export function isInProgress(step: IrisExercisePlanStep): boolean {
    return getExecutionStage(step) === ExecutionStage.IN_PROGRESS;
}

export function isFailed(step: IrisExercisePlanStep): boolean {
    return getExecutionStage(step) === ExecutionStage.FAILED;
}

export function isComplete(step: IrisExercisePlanStep): boolean {
    return getExecutionStage(step) === ExecutionStage.COMPLETE;
}

export function hideOrUnhide(step: IrisExercisePlanStep): void {
    step.hidden = !isHidden(step);
}

export function isHidden(step: IrisExercisePlanStep): boolean {
    return step.hidden ?? true; // default to true if undefined
}

export enum ExerciseComponent {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
    SOLUTION_REPOSITORY = 'SOLUTION_REPOSITORY',
    TEMPLATE_REPOSITORY = 'TEMPLATE_REPOSITORY',
    TEST_REPOSITORY = 'TEST_REPOSITORY',
}

export enum ExecutionStage {
    NOT_EXECUTED = 'NOT_EXECUTED',
    IN_PROGRESS = 'IN_PROGRESS',
    FAILED = 'FAILED',
    COMPLETE = 'COMPLETE',
}

export function isTextContent(content: IrisMessageContent): content is IrisTextMessageContent {
    return content.type === IrisMessageContentType.TEXT;
}

export function getTextContent(content: IrisMessageContent) {
    if (isTextContent(content)) {
        const irisMessageTextContent = content as IrisTextMessageContent;
        return irisMessageTextContent.textContent;
    }
}

export function isExercisePlan(content: IrisMessageContent): content is IrisExercisePlan {
    return content.type === IrisMessageContentType.EXERCISE_PLAN;
}
