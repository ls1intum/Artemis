import { BaseEntity, User } from './../../shared';

export const enum ParticipationState {
    'UNINITIALIZED',
    'REPO_COPIED',
    'REPO_CONFIGURED',
    'BUILD_PLAN_COPIED',
    'BUILD_PLAN_CONFIGURED',
    'INITIALIZED'
}

export class Participation implements BaseEntity {
    constructor(
        public id?: number,
        public repositoryUrl?: string,
        public buildPlanId?: string,
        public initializationState?: ParticipationState,
        public initializationDate?: any,
        public results?: BaseEntity[],
        public student?: User,
        public exercise?: BaseEntity,
    ) {
    }
}
