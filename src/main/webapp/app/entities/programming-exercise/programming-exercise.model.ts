import { BaseEntity } from './../../shared';

export class ProgrammingExercise implements BaseEntity {
    constructor(
        public id?: number,
        public baseRepositoryUrl?: string,
        public baseBuildPlanId?: string,
        public publishBuildPlanUrl?: boolean,
        public allowOnlineEditor?: boolean,
    ) {
        this.publishBuildPlanUrl = false;
        this.allowOnlineEditor = false;
    }
}
