import { IrisExercisePlanComponent } from 'app/entities/iris/iris-exercise-plan-component.model';

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

export class IrisMessageTextContent extends IrisMessageContent {
    textContent: string;
    constructor(textContent: string) {
        super(IrisMessageContentType.TEXT);
        this.textContent = textContent;
    }
}

export class IrisMessagePlanContent extends IrisMessageContent {
    components: IrisExercisePlanComponent[];
    currentComponentIndex: number;
    constructor(components: IrisExercisePlanComponent[], currentComponentIndex: number) {
        super(IrisMessageContentType.PLAN);
        this.components = components;
        this.currentComponentIndex = currentComponentIndex;
    }
}

export function isTextContent(content: IrisMessageContent) {
    return content instanceof IrisMessageTextContent;
}

export function getTextContent(content: IrisMessageContent) {
    if (content instanceof IrisMessageTextContent) {
        const irisMessageTextContent = content;
        return irisMessageTextContent.textContent;
    }
}
