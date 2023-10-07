import { IrisExercisePlanComponent } from 'app/entities/iris/iris-exercise-plan-component.model';

export enum IrisMessageContentType {
    TEXT = 'text',
    PLAN = 'plan',
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
    constructor() {
        super(IrisMessageContentType.TEXT);
    }
}

export class IrisMessagePlanContent extends IrisMessageContent {
    components: IrisExercisePlanComponent[];
    currentComponentIndex: number;
    constructor() {
        super(IrisMessageContentType.PLAN);
    }
}
