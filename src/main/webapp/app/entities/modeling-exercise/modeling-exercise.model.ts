import { Exercise } from '../exercise';
import { Course } from '../course';

export class ModelingExercise extends Exercise {
    constructor(
        public id?: number,
        public type?: string,
        public title?: string,
        public releaseDate?: any,
        public dueDate?: any,
        public maxScore?: number,
        public course?: Course,
        public description?: string,
    ) {
        super();
    }
}
