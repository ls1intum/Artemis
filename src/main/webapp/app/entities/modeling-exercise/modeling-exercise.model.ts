import { Exercise } from '../exercise';
import { Course } from '../course';

export const enum DiagramType {
    'CLASS',
    'ACTIVITY',
    'USE_CASE',
    'COMMUNICATION'
}

export class ModelingExercise extends Exercise {
    constructor(
        public id?: number,
        public type?: string,
        public title?: string,
        public releaseDate?: any,
        public dueDate?: any,
        public maxScore?: number,
        public diagramType?: DiagramType,
        public sampleSolutionModel?: String,
        public sampleSolutionExplanation?: String,
        public course?: Course,
        public description?: string,
    ) {
        super();
    }
}
