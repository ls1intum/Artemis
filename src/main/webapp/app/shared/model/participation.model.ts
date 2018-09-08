import { Moment } from 'moment';
import { IResult } from 'app/shared/model/result.model';
import { IUser } from 'app/core/user/user.model';
import { IExercise } from 'app/shared/model/exercise.model';

export const enum ParticipationState {
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
    initializationState?: ParticipationState;
    initializationDate?: Moment;
    results?: IResult[];
    student?: IUser;
    exercise?: IExercise;
}

export class Participation implements IParticipation {
    constructor(
        public id?: number,
        public repositoryUrl?: string,
        public buildPlanId?: string,
        public initializationState?: ParticipationState,
        public initializationDate?: Moment,
        public results?: IResult[],
        public student?: IUser,
        public exercise?: IExercise
    ) {}
}
