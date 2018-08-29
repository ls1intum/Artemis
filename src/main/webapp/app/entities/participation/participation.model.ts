import { BaseEntity, User } from './../../shared';
import { Exercise } from '../exercise';
import { Result } from '../result';
import { Submission } from '../submission';

export const enum InitializationState {
    UNINITIALIZED = 'UNINITIALIZED',
    REPO_COPIED = 'REPO_COPIED',
    REPO_CONFIGURED = 'REPO_CONFIGURED',
    BUILD_PLAN_COPIED = 'BUILD_PLAN_COPIED',
    BUILD_PLAN_CONFIGURED = 'BUILD_PLAN_CONFIGURED',
    INITIALIZED = 'INITIALIZED',
    FINISHED = 'FINISHED'
}

export class Participation implements BaseEntity {
    public id: number;

    public repositoryUrl: string;
    public buildPlanId: string;
    public initializationState: InitializationState;
    public initializationDate: any;
    public presentationScore: number;
    public results: Result[];
    public submissions: Submission[];
    public student: User;
    public exercise: Exercise;

    constructor() {
    }
}
