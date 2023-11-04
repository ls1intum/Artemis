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
    executionStage: ExecutionStage;
    hidden?: boolean; // Client-side only
}

export function isNotExecuted(step: IrisExercisePlanStep): boolean {
    return step.executionStage === ExecutionStage.NOT_EXECUTED;
}

export function isInProgress(step: IrisExercisePlanStep): boolean {
    return step.executionStage === ExecutionStage.IN_PROGRESS;
}

export function isFailed(step: IrisExercisePlanStep): boolean {
    return step.executionStage === ExecutionStage.FAILED;
}

export function isComplete(step: IrisExercisePlanStep): boolean {
    return step.executionStage === ExecutionStage.COMPLETE;
}

export function hideOrUnhide(step: IrisExercisePlanStep): void {
    step.hidden = !isHidden(step);
}

export function isHidden(step: IrisExercisePlanStep): boolean {
    if (step.hidden === undefined) {
        // When a plan is first loaded, all steps are hidden by default
        step.hidden = true;
    }
    return step.hidden;
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
