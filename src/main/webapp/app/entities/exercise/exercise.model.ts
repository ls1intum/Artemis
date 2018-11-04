import { BaseEntity } from 'app/shared';
import { Course } from '../course';
import { Participation } from '../participation';
import * as moment from 'moment';
import { Moment } from 'moment';
import { EntityArrayResponseType, EntityResponseType } from 'app/entities/exercise/exercise.service';

export const enum DifficultyLevel {
    EASY = 'EASY',
    MEDIUM = 'MEDIUM',
    HARD = 'HARD'
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Exercise.java
export const enum ExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload'
}

export const enum ParticipationStatus {
    QUIZ_UNINITIALIZED = 'quiz-uninitialized',
    QUIZ_ACTIVE = 'quiz-active',
    QUIZ_SUBMITTED = 'quiz-submitted',
    QUIZ_NOT_STARTED = 'quiz-not-started',
    QUIZ_NOT_PARTICIPATED = 'quiz-not-participated',
    QUIZ_FINISHED = 'quiz-finished',
    MODELING_EXERCISE = 'modeling-exercise',
    UNINITIALIZED = 'uninitialized',
    INITIALIZED = 'initialized',
    INACTIVE = 'inactive'
}

export abstract class Exercise implements BaseEntity {
    public id: number;
    public problemStatement: string;
    public gradingInstructions: string;
    public title: string;
    public releaseDate: Moment;
    public dueDate: Moment;
    public maxScore: number;
    public difficulty: DifficultyLevel;
    public categories: string[];
    public participations: Participation[];
    public course: Course;
    public participationStatus: ParticipationStatus;
    public type: ExerciseType;

    // helper attributes
    public isAtLeastTutor: boolean;
    public loading: boolean;

    protected static convertDateFromClient<E extends Exercise>(exercise: E): E {
        return Object.assign({}, exercise, {
            releaseDate: exercise.releaseDate != null && moment(exercise.releaseDate).isValid() ? exercise.releaseDate.toJSON() : null,
            dueDate: exercise.dueDate != null && moment(exercise.dueDate).isValid() ? exercise.dueDate.toJSON() : null
        });
    }

    protected static convertDateFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate != null ? moment(res.body.releaseDate) : null;
            res.body.dueDate = res.body.dueDate != null ? moment(res.body.dueDate) : null;
        }
        return res;
    }

    protected static convertDateArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((exercise: E) => {
                exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
                exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
            });
        }
        return res;
    }

    protected constructor(type: ExerciseType) {
        this.type = type;
    }
}
