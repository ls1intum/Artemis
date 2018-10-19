import { Moment } from 'moment';
import { IExerciseResult } from 'app/shared/model//exercise-result.model';
import { ISubmission } from 'app/shared/model//submission.model';
import { IUser } from 'app/core/user/user.model';
import { IExercise } from 'app/shared/model//exercise.model';

export const enum InitializationState {
    UNINITIALIZED = 'UNINITIALIZED',
    REPO_COPIED = 'REPO_COPIED',
    REPO_CONFIGURED = 'REPO_CONFIGURED',
    BUILD_PLAN_COPIED = 'BUILD_PLAN_COPIED',
    BUILD_PLAN_CONFIGURED = 'BUILD_PLAN_CONFIGURED',
    INITIALIZED = 'INITIALIZED'
}

export interface IParticipation {
    id?: number;
    repositoryUrl?: string;
    buildPlanId?: string;
    initializationState?: InitializationState;
    initializationDate?: Moment;
    presentationScore?: number;
    results?: IExerciseResult[];
    submissions?: ISubmission[];
    student?: IUser;
    exercise?: IExercise;
}

export class Participation implements IParticipation {
    constructor(
        public id?: number,
        public repositoryUrl?: string,
        public buildPlanId?: string,
        public initializationState?: InitializationState,
        public initializationDate?: Moment,
        public presentationScore?: number,
        public results?: IExerciseResult[],
        public submissions?: ISubmission[],
        public student?: IUser,
        public exercise?: IExercise
    ) {}
}
