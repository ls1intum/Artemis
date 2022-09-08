import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Submission } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';

export const enum InitializationState {
    /**
     * The InitializationState enumeration.
     * INITIALIZED: The participation is set up for submissions from the student
     * FINISHED: Text- / Modelling: At least one submission is done. Quiz: No further submissions should be possible
     */
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
    public id?: number;

    public initializationState?: InitializationState;
    public initializationDate?: dayjs.Dayjs;
    public individualDueDate?: dayjs.Dayjs;
    public presentationScore?: number;
    public results?: Result[]; // TODO: is this result array ordered by date?
    public submissions?: Submission[];
    public exercise?: Exercise;
    public type?: ParticipationType;

    // workaround for strict template here, only used in case of StudentParticipation
    public participantName?: string;
    public participantIdentifier?: string;

    // transient
    public submissionCount?: number;

    constructor(type: ParticipationType) {
        this.type = type;
    }
}

/**
 * Gets the exercise of a given participation, if possible.
 *
 * This is needed as different participation types may use different exercise attributes (e.g. not 'exercise' but 'programmingExercise').
 */
export const getExercise = (participation: Participation): Exercise | undefined => {
    if (participation) {
        switch (participation.type) {
            case ParticipationType.PROGRAMMING:
                return (participation as ProgrammingExerciseStudentParticipation).exercise;
            case ParticipationType.STUDENT:
                return (participation as StudentParticipation).exercise;
            case ParticipationType.SOLUTION:
                return (participation as SolutionProgrammingExerciseParticipation).programmingExercise;
            case ParticipationType.TEMPLATE:
                return (participation as TemplateProgrammingExerciseParticipation).programmingExercise;
        }
    }
};
