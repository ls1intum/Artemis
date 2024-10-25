import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faBrain, faComments, faCubesStacked, faMagnifyingGlass, faPenFancy, faPlusMinus, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';

/**
 * The available competency types (based on Bloom's Taxonomy)
 */
export enum CompetencyTaxonomy {
    REMEMBER = 'REMEMBER',
    UNDERSTAND = 'UNDERSTAND',
    APPLY = 'APPLY',
    ANALYZE = 'ANALYZE',
    EVALUATE = 'EVALUATE',
    CREATE = 'CREATE',
}

export enum CompetencyRelationType {
    ASSUMES = 'ASSUMES',
    EXTENDS = 'EXTENDS',
    MATCHES = 'MATCHES',
}

export enum CompetencyRelationError {
    CIRCULAR = 'CIRCULAR',
    SELF = 'SELF',
    EXISTING = 'EXISTING',
}

export enum CourseCompetencyValidators {
    TITLE_MAX = 255,
    DESCRIPTION_MAX = 10000,
    MASTERY_THRESHOLD_MIN = 0,
    MASTERY_THRESHOLD_MAX = 100,
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in CourseCompetency.java
export enum CourseCompetencyType {
    COMPETENCY = 'competency',
    PREREQUISITE = 'prerequisite',
}

export const DEFAULT_MASTERY_THRESHOLD = 100;

export const MEDIUM_COMPETENCY_LINK_WEIGHT = 0.5;

export abstract class BaseCompetency implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
}

export interface UpdateCourseCompetencyRelationDTO {
    newRelationType: CompetencyRelationType;
}

export abstract class CourseCompetency extends BaseCompetency {
    softDueDate?: dayjs.Dayjs;
    masteryThreshold?: number;
    optional?: boolean;
    linkedStandardizedCompetency?: StandardizedCompetency;
    exerciseLinks?: CompetencyExerciseLink[];
    lectureUnitLinks?: CompetencyLectureUnitLink[];
    userProgress?: CompetencyProgress[];
    courseProgress?: CourseCompetencyProgress;
    course?: Course;
    linkedCourseCompetency?: CourseCompetency;

    public type?: CourseCompetencyType;

    protected constructor(type: CourseCompetencyType) {
        super();
        this.type = type;
    }
}

export class Competency extends CourseCompetency {
    constructor() {
        super(CourseCompetencyType.COMPETENCY);
    }
}

export class CompetencyLearningObjectLink {
    competency?: CourseCompetency;
    weight: number;

    constructor(competency: CourseCompetency | undefined, weight: number) {
        this.competency = competency;
        this.weight = weight;
    }
}

export class CompetencyExerciseLink extends CompetencyLearningObjectLink {
    exercise?: Exercise;

    constructor(competency: CourseCompetency | undefined, exercise: Exercise | undefined, weight: number) {
        super(competency, weight);
        this.exercise = exercise;
    }
}

export class CompetencyLectureUnitLink extends CompetencyLearningObjectLink {
    lectureUnit?: LectureUnit;

    constructor(competency: CourseCompetency | undefined, lectureUnit: LectureUnit | undefined, weight: number) {
        super(competency, weight);
        this.lectureUnit = lectureUnit;
    }
}

export class CompetencyJol {
    competencyId: number;
    jolValue: number;
    judgementTime: string;
    competencyProgress: number;
    competencyConfidence: number;

    static shouldPromptForJol(competency: Competency, progress: CompetencyProgress | undefined, courseCompetencies: Competency[]): boolean {
        const currentDate = dayjs();
        const softDueDateMinusOneDay = competency.softDueDate?.subtract(1, 'day');
        const competencyProgress = progress?.progress ?? 0;

        // Precondition: Student has at least some progress on the competency
        if (competencyProgress === undefined || competencyProgress === 0) {
            return false;
        }

        // Condition 1: Current Date >= Competency Soft Due Date - 1 Days && Competency Progress >= 20%
        if (softDueDateMinusOneDay && currentDate.isAfter(softDueDateMinusOneDay) && competencyProgress >= 20) {
            return true;
        }

        // Filter previous competencies (those with soft due date in the past)
        const previousCompetencies = courseCompetencies.filter((c) => c.softDueDate && c.softDueDate.isBefore(currentDate));
        if (previousCompetencies.length === 0) {
            if (softDueDateMinusOneDay) {
                return false;
            } else {
                return competencyProgress >= 20;
            }
        }

        // Calculate the average progress of all previous competencies
        const totalPreviousProgress = previousCompetencies.reduce((sum, c) => {
            const progress = c.userProgress?.first()?.progress ?? 0;
            return sum + progress;
        }, 0);
        const avgPreviousProgress = totalPreviousProgress / previousCompetencies.length;

        // Condition 2: Competency Progress >= 0.8 * Avg. Progress of all previous competencies
        return competencyProgress >= 0.8 * avgPreviousProgress;
    }
}

export interface CompetencyImportResponseDTO extends BaseEntity {
    title?: string;
    description?: string;
    softDueDate?: dayjs.Dayjs;
    taxonomy?: CompetencyTaxonomy;
    masteryThreshold?: number;
    optional?: boolean;
    courseId?: number;
    linkedStandardizedCompetencyId?: number;
}

export enum ConfidenceReason {
    NO_REASON = 'NO_REASON',
    RECENT_SCORES_LOWER = 'RECENT_SCORES_LOWER',
    RECENT_SCORES_HIGHER = 'RECENT_SCORES_HIGHER',
    MORE_EASY_POINTS = 'MORE_EASY_POINTS',
    MORE_HARD_POINTS = 'MORE_HARD_POINTS',
    QUICKLY_SOLVED_EXERCISES = 'QUICKLY_SOLVED_EXERCISES',
}

export class CompetencyProgress {
    public progress?: number;
    public confidence?: number;
    public confidenceReason?: ConfidenceReason;

    constructor() {}
}

export class CourseCompetencyProgress {
    competencyId?: number;
    numberOfStudents?: number;
    numberOfMasteredStudents?: number;
    averageStudentScore?: number;

    constructor() {}
}

export class CompetencyRelation implements BaseEntity {
    public id?: number;
    public tailCompetency?: CourseCompetency;
    public headCompetency?: CourseCompetency;
    public type?: CompetencyRelationType;
}

export interface CourseCompetencyImportOptionsDTO {
    competencyIds: number[];
    sourceCourseId?: number;
    importRelations: boolean;
    importExercises: boolean;
    importLectures: boolean;
    referenceDate?: dayjs.Dayjs;
    isReleaseDate?: boolean;
}

export class CompetencyRelationDTO implements BaseEntity {
    id?: number;
    tailCompetencyId?: number;
    headCompetencyId?: number;
    relationType?: CompetencyRelationType;
}

/**
 * Converts a CompetencyRelationDTO to a CompetencyRelation
 * @param competencyRelationDTO
 */
export function dtoToCompetencyRelation(competencyRelationDTO: CompetencyRelationDTO) {
    const relation: CompetencyRelation = {
        id: competencyRelationDTO.id,
        tailCompetency: { id: competencyRelationDTO.tailCompetencyId },
        headCompetency: { id: competencyRelationDTO.headCompetencyId },
        type: competencyRelationDTO.relationType,
    };
    return relation;
}

export class CompetencyWithTailRelationDTO {
    competency?: CourseCompetency;
    tailRelations?: CompetencyRelationDTO[];

    constructor() {}
}

export function getIcon(competencyTaxonomy?: CompetencyTaxonomy): IconProp {
    if (!competencyTaxonomy) {
        return faQuestion as IconProp;
    }

    const icons = {
        [CompetencyTaxonomy.REMEMBER]: faBrain,
        [CompetencyTaxonomy.UNDERSTAND]: faComments,
        [CompetencyTaxonomy.APPLY]: faPenFancy,
        [CompetencyTaxonomy.ANALYZE]: faMagnifyingGlass,
        [CompetencyTaxonomy.EVALUATE]: faPlusMinus,
        [CompetencyTaxonomy.CREATE]: faCubesStacked,
    };

    return icons[competencyTaxonomy] as IconProp;
}

/**
 * The progress depends on the amount of completed lecture units and the achieved scores in the exercises.
 * @param competencyProgress The progress in the competency
 */
export function getProgress(competencyProgress: CompetencyProgress | undefined): number {
    return Math.round(competencyProgress?.progress ?? 0);
}

/**
 * The confidence is a factor for the progress, normally near 1. It depends on different heuristics and determines if the mastery is lower/higher than the progress.
 * @param competencyProgress The progress in the competency
 */
export function getConfidence(competencyProgress: CompetencyProgress | undefined): number {
    return competencyProgress?.confidence ?? 1;
}

/**
 * The mastery is the final value that is shown to the user. It is the product of the progress and the confidence.
 * @param competencyProgress The progress in the competency
 */
export function getMastery(competencyProgress: CompetencyProgress | undefined): number {
    // clamp the value between 0 and 100
    return Math.min(100, Math.max(0, Math.round(getProgress(competencyProgress) * getConfidence(competencyProgress))));
}
