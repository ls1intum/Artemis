import { BaseEntity } from 'app/shared';
import { Result } from '../result';
import { Submission } from '../submission';
import { Moment } from 'moment';
import { Exercise } from 'app/entities/exercise';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { TeamParticipation } from 'app/entities/participation/team-participation.model';
import { ProgrammingExerciseTeamParticipation } from 'app/entities/participation/programming-exercise-team-participation.model';
import { ProgrammingExerciseAgentParticipation } from 'app/entities/participation/programming-exercise-agent-participation.model';
import { AgentParticipation } from 'app/entities/participation/agent-participation.model';

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
    TEAM = 'team',
    AGENT = 'agent',
    PROGRAMMING_STUDENT = 'programming-student',
    PROGRAMMING_TEAM = 'programming-team',
    PROGRAMMING_AGENT = 'programming-agent',
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
    public exercise?: Exercise;
    public type: ParticipationType;

    // transient
    public submissionCount?: number;

    constructor(type: ParticipationType) {
        this.type = type;
    }
}

export const getExercise = (participation: Participation): Exercise => {
    switch (participation.type) {
        case ParticipationType.PROGRAMMING_STUDENT:
            return (participation as ProgrammingExerciseStudentParticipation).exercise;
        case ParticipationType.PROGRAMMING_TEAM:
            return (participation as ProgrammingExerciseTeamParticipation).exercise;
        case ParticipationType.PROGRAMMING_AGENT:
            return (participation as ProgrammingExerciseAgentParticipation).exercise;
        case ParticipationType.STUDENT:
            return (participation as StudentParticipation).exercise;
        case ParticipationType.TEAM:
            return (participation as TeamParticipation).exercise;
        case ParticipationType.AGENT:
            return (participation as AgentParticipation).exercise;
        case ParticipationType.SOLUTION:
            return (participation as SolutionProgrammingExerciseParticipation).programmingExercise;
        case ParticipationType.TEMPLATE:
            return (participation as TemplateProgrammingExerciseParticipation).programmingExercise;
    }
};
