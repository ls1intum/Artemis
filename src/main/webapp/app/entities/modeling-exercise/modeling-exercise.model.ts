import { Exercise } from '../exercise';
import { Course } from '../course';

export const enum DiagramType {
    CLASS = 'CLASS',
    ACTIVITY = 'ACTIVITY',
    USE_CASE = 'USE_CASE',
    COMMUNICATION = 'COMMUNICATION'
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
        public course?: Course,
        public description?: string,
    ) {
        super();
    }
}
