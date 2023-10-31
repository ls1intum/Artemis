import { IrisExercisePlanStep } from 'app/entities/iris/iris-exercise-plan-component.model';

export enum IrisMessageContentType {
    TEXT = 'text',
    PLAN = 'exercise-plan',
}

export abstract class IrisMessageContent {
    id?: number;
    type?: IrisMessageContentType;
    protected constructor(type: IrisMessageContentType) {
        this.type = type;
    }
}

export class IrisTextMessageContent extends IrisMessageContent {
    textContent: string;
    constructor(textContent: string) {
        super(IrisMessageContentType.TEXT);
        this.textContent = textContent;
    }
}

export class IrisExercisePlan extends IrisMessageContent {
    steps: IrisExercisePlanStep[];
    currentStepIndex: number;
    constructor(components: IrisExercisePlanStep[], currentComponentIndex: number) {
        super(IrisMessageContentType.PLAN);
        this.steps = components;
        this.currentStepIndex = currentComponentIndex;
    }
}

export function isTextContent(content: IrisMessageContent) {
    return content.type === IrisMessageContentType.TEXT;
}

export function getTextContent(content: IrisMessageContent) {
    if (isTextContent(content)) {
        const irisMessageTextContent = content as IrisTextMessageContent;
        return irisMessageTextContent.textContent;
    }
}

export function isPlanContent(content: IrisMessageContent) {
    return content.type === IrisMessageContentType.PLAN;
}

export function getPlanSteps(content: IrisMessageContent) {
    if (isPlanContent(content)) {
        const irisMessagePlanContent = content as IrisExercisePlan;
        return irisMessagePlanContent.steps;
    }
}

export function getComponentInstruction(component: IrisExercisePlanStep) {
    return component.instructions;
}
