export const enum DiagramType {
    CLASS = 'CLASS',
    ACTIVITY = 'ACTIVITY',
    USE_CASE = 'USE_CASE',
    COMMUNICATION = 'COMMUNICATION'
}

export interface IModelingExercise {
    id?: number;
    diagramType?: DiagramType;
    sampleSolutionModel?: string;
    sampleSolutionExplanation?: string;
}

export class ModelingExercise implements IModelingExercise {
    constructor(
        public id?: number,
        public diagramType?: DiagramType,
        public sampleSolutionModel?: string,
        public sampleSolutionExplanation?: string
    ) {}
}
