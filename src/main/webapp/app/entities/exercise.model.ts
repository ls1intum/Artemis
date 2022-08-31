import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { TutorParticipation } from 'app/entities/participation/tutor-participation.model';
import { Course } from 'app/entities/course.model';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { Attachment } from 'app/entities/attachment.model';
import { Post } from 'app/entities/metis/post.model';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { Team } from 'app/entities/team.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ExerciseInfo } from 'app/exam/exam-scores/exam-score-dtos.model';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram, faQuestion } from '@fortawesome/free-solid-svg-icons';

export enum DifficultyLevel {
    EASY = 'EASY',
    MEDIUM = 'MEDIUM',
    HARD = 'HARD',
}

export enum ExerciseMode {
    INDIVIDUAL = 'INDIVIDUAL',
    TEAM = 'TEAM',
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Exercise.java
export enum ExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload',
}

export interface ValidationReason {
    translateKey: string;
    translateValues: any;
}

export const exerciseTypes: ExerciseType[] = [ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.PROGRAMMING, ExerciseType.FILE_UPLOAD, ExerciseType.QUIZ];

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Exercise.java
export enum IncludedInOverallScore {
    INCLUDED_COMPLETELY = 'INCLUDED_COMPLETELY',
    INCLUDED_AS_BONUS = 'INCLUDED_AS_BONUS',
    NOT_INCLUDED = 'NOT_INCLUDED',
}

export enum ParticipationStatus {
    QUIZ_UNINITIALIZED = 'quiz-uninitialized',
    QUIZ_ACTIVE = 'quiz-active',
    QUIZ_SUBMITTED = 'quiz-submitted',
    QUIZ_NOT_STARTED = 'quiz-not-started',
    QUIZ_NOT_PARTICIPATED = 'quiz-not-participated',
    QUIZ_FINISHED = 'quiz-finished',
    NO_TEAM_ASSIGNED = 'no-team-assigned',
    UNINITIALIZED = 'uninitialized',
    INITIALIZED = 'initialized',
    INACTIVE = 'inactive',
    EXERCISE_ACTIVE = 'exercise-active',
    EXERCISE_SUBMITTED = 'exercise-submitted',
    EXERCISE_MISSED = 'exercise-missed',
}

export abstract class Exercise implements BaseEntity {
    public id?: number;
    public problemStatement?: string;
    public gradingInstructions?: string;
    public title?: string;
    public shortName?: string;
    public releaseDate?: dayjs.Dayjs;
    public dueDate?: dayjs.Dayjs;
    public assessmentDueDate?: dayjs.Dayjs;
    public maxPoints?: number;
    public bonusPoints?: number;
    public assessmentType?: AssessmentType;
    public allowComplaintsForAutomaticAssessments?: boolean;
    public difficulty?: DifficultyLevel;
    public mode?: ExerciseMode = ExerciseMode.INDIVIDUAL; // default value
    public includedInOverallScore?: IncludedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY; // default value
    public teamAssignmentConfig?: TeamAssignmentConfig;
    public categories?: ExerciseCategory[];
    public type?: ExerciseType;
    public exampleSolutionPublicationDate?: dayjs.Dayjs;

    public teams?: Team[];
    public studentParticipations?: StudentParticipation[];
    public tutorParticipations?: TutorParticipation[];
    public course?: Course;
    public participationStatus?: ParticipationStatus;
    public exampleSubmissions?: ExampleSubmission[];
    public attachments?: Attachment[];
    public posts?: Post[];
    public gradingCriteria?: GradingCriterion[];
    public exerciseGroup?: ExerciseGroup;
    public learningGoals?: LearningGoal[];

    // transient objects which might not be set
    public numberOfSubmissions?: DueDateStat;
    public totalNumberOfAssessments?: DueDateStat;
    public numberOfAssessmentsOfCorrectionRounds = [new DueDateStat()]; // Array with number of assessments for each correction round
    public numberOfComplaints?: number;
    public numberOfOpenComplaints?: number;
    public numberOfMoreFeedbackRequests?: number;
    public numberOfOpenMoreFeedbackRequests?: number;
    public studentAssignedTeamId?: number;
    public studentAssignedTeamIdComputed = false;
    public numberOfParticipations?: number;
    public testRunParticipationsExist?: boolean;
    public averageRating?: number;
    public numberOfRatings?: number;

    // helper attributes
    public secondCorrectionEnabled = false;
    public isAtLeastTutor?: boolean;
    public isAtLeastEditor?: boolean;
    public isAtLeastInstructor?: boolean;
    public teamMode?: boolean;
    public assessmentDueDateError?: boolean;
    public dueDateError?: boolean;
    public exampleSolutionPublicationDateError?: boolean;
    public exampleSolutionPublicationDateWarning?: boolean;
    public loading?: boolean;
    public numberOfParticipationsWithRatedResult?: number;
    public numberOfSuccessfulParticipations?: number;
    public averagePoints?: number;
    public presentationScoreEnabled?: boolean;
    public gradingInstructionFeedbackUsed?: boolean;
    public exampleSolutionPublished?: boolean;

    protected constructor(type: ExerciseType) {
        this.type = type;
        this.bonusPoints = 0; // default value
        this.isAtLeastTutor = false; // default value
        this.isAtLeastEditor = false; // default value
        this.isAtLeastInstructor = false; // default value
        this.teamMode = false; // default value
        this.assessmentDueDateError = false;
        this.dueDateError = false;
        this.exampleSolutionPublicationDateError = false;
        this.presentationScoreEnabled = false; // default value;
        this.allowComplaintsForAutomaticAssessments = false; // default value;
    }

    /**
     * Sanitize exercise attributes.
     * This method should be used before sending an exercise to the server.
     *
     * @param exercise
     */
    public static sanitize<T extends Exercise>(exercise: T): T {
        exercise.title = exercise.title?.trim();
        return exercise;
    }
}

export function getIcon(exerciseType?: ExerciseType): IconProp {
    if (!exerciseType) {
        return faQuestion as IconProp;
    }

    const icons = {
        [ExerciseType.PROGRAMMING]: faKeyboard,
        [ExerciseType.MODELING]: faProjectDiagram,
        [ExerciseType.QUIZ]: faCheckDouble,
        [ExerciseType.TEXT]: faFont,
        [ExerciseType.FILE_UPLOAD]: faFileUpload,
    };

    return icons[exerciseType] as IconProp;
}

export function getIconTooltip(exerciseType?: ExerciseType): string {
    if (!exerciseType) {
        return '';
    }
    const tooltips = {
        [ExerciseType.PROGRAMMING]: 'artemisApp.exercise.isProgramming',
        [ExerciseType.MODELING]: 'artemisApp.exercise.isModeling',
        [ExerciseType.QUIZ]: 'artemisApp.exercise.isQuiz',
        [ExerciseType.TEXT]: 'artemisApp.exercise.isText',
        [ExerciseType.FILE_UPLOAD]: 'artemisApp.exercise.isFileUpload',
    };

    return tooltips[exerciseType];
}

/**
 * Get the course id for an exercise.
 * The course id is extracted from the course of the exercise if present, if not present (exam mode), it is extracted from the corresponding exam.
 * @param exercise the exercise for which the course id should be extracted
 */
export function getCourseId(exercise: Exercise): number | undefined {
    return getCourseFromExercise(exercise)?.id;
}

/**
 * Get the course for an exercise.
 * The course is extracted from the course of the exercise if present, if not present (exam mode), it is extracted from the corresponding exam.
 * @param exercise the exercise for which the course should be extracted
 */
export function getCourseFromExercise(exercise: Exercise): Course | undefined {
    return exercise.course || exercise.exerciseGroup?.exam?.course;
}

/**
 * In order to create an ExerciseType enum, we take the ExerciseInfo (which can be fetched from the server) and map it to the ExerciseType
 * @param exerciseInfo the exercise information which is given by the server java class
 * @return ExerciseType or undefined if the exerciseInfo does not match
 */
export function declareExerciseType(exerciseInfo: ExerciseInfo): ExerciseType | undefined {
    switch (exerciseInfo.exerciseType) {
        case 'TextExercise':
            return ExerciseType.TEXT;
        case 'ModelingExercise':
            return ExerciseType.MODELING;
        case 'ProgrammingExercise':
            return ExerciseType.PROGRAMMING;
        case 'FileUploadExercise':
            return ExerciseType.FILE_UPLOAD;
        case 'QuizExercise':
            return ExerciseType.QUIZ;
    }
    return undefined;
}

/**
 * Get the url segment for different types of exercises.
 * @param exerciseType The type of the exercise
 * @return The url segment for the exercise type
 */
export function getExerciseUrlSegment(exerciseType?: ExerciseType): string {
    switch (exerciseType) {
        case ExerciseType.TEXT:
            return 'text-exercises';
        case ExerciseType.MODELING:
            return 'modeling-exercises';
        case ExerciseType.PROGRAMMING:
            return 'programming-exercises';
        case ExerciseType.FILE_UPLOAD:
            return 'file-upload-exercises';
        case ExerciseType.QUIZ:
            return 'quiz-exercises';
        default:
            throw Error('Unexpected exercise type: ' + exerciseType);
    }
}

export function resetDates(exercise: Exercise) {
    exercise.releaseDate = undefined;
    exercise.dueDate = undefined;
    exercise.assessmentDueDate = undefined;
    exercise.exampleSolutionPublicationDate = undefined;
}
