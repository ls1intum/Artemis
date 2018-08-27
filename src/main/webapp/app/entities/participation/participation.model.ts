import { BaseEntity, User } from './../../shared';
import { Exercise } from '../exercise';
import { Result } from '../result';
import { Submission } from '../submission';

export const enum ParticipationState {
    'UNINITIALIZED',
    'REPO_COPIED',
    'REPO_CONFIGURED',
    'BUILD_PLAN_COPIED',
    'BUILD_PLAN_CONFIGURED',
    'INITIALIZED',
    'FINISHED'
}

export class Participation implements BaseEntity {
    constructor(
        public id?: number,
        public repositoryUrl?: string,
        public buildPlanId?: string,
        public initializationState?: ParticipationState,
        public initializationDate?: any,
        public presentationScore?: number,
        public results?: Result[],
        public submissions?: Submission[],
        public student?: User,
        public exercise?: Exercise,
    ) {
    }
}
