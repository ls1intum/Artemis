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
        if (publishBuildPlanUrl == null) {
            this.publishBuildPlanUrl = false;   //default value for null or undefined
        }
        if (!allowOnlineEditor == null) {
            this.allowOnlineEditor = false;     //default value for null or undefined
        }
    }
}
