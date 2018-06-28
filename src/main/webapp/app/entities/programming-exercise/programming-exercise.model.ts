import { Course } from '../course';
import { Exercise } from '../exercise';

export class ProgrammingExercise extends Exercise {
    constructor(
        public id?: number,
        public title?: string,
        public type?: string,
        public releaseDate?: any,
        public dueDate?: any,
        public maxScore?: number,
        public baseRepositoryUrl?: string,
        public baseBuildPlanId?: string,
        public publishBuildPlanUrl?: boolean,
        public allowOnlineEditor?: boolean,
        public course?: Course
    ) {
        super();
        this.publishBuildPlanUrl = false;
        this.allowOnlineEditor = false;
    }
}
