import { User } from './../../core';
import { BaseEntity } from 'app/shared';
import { Exercise } from '../exercise';
import { Result } from '../result';
import { Submission } from '../submission';
import { Moment } from 'moment';

export const enum InitializationState {
    UNINITIALIZED = 'UNINITIALIZED',
    REPO_COPIED = 'REPO_COPIED',
    REPO_CONFIGURED = 'REPO_CONFIGURED',
    BUILD_PLAN_COPIED = 'BUILD_PLAN_COPIED',
    BUILD_PLAN_CONFIGURED = 'BUILD_PLAN_CONFIGURED',
    INITIALIZED = 'INITIALIZED',
    FINISHED = 'FINISHED',
    INACTIVE = 'INACTIVE',
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Participation.java
export enum ParticipationType {
    STUDENT = 'student',
    PROGRAMMING = 'programming',
    TEMPLATE = 'template',
    SOLUTION = 'solution',
}

export abstract class Participation implements BaseEntity {
    public id: number;

    public initializationState: InitializationState;
    public initializationDate: Moment | null;
    public presentationScore: number;
    public results: Result[];
    public submissions: Submission[];
    public type: ParticipationType;
    public exercise: Exercise;

    constructor(type: ParticipationType) {
        this.type = type;
    }
}
