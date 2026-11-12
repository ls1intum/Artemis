import {
    Competency,
    CompetencyExerciseLink,
    CompetencyLectureUnitLink,
    CompetencyProgress,
    CompetencyRelationDTO,
    CompetencyRelationType,
    CompetencyTaxonomy,
    CompetencyWithTailRelationDTO,
    ConfidenceReason,
    CourseCompetency,
    CourseCompetencyType,
} from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { Course } from 'app/course/shared/entities/course.model';
import { DifficultyLevel, Exercise, ExerciseMode, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import type { UMLDiagramType } from '@tumaet/apollon';
import { ExerciseCategory, SerializedExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/foundation/util/date.utils';

import { parseJson } from 'app/foundation/util/json.util';
import type { AttachmentForCompetencyAttachmentTypeEnum } from 'app/openapi/model/attachment-for-competency';
import type { CompetencyProgressConfidenceReasonEnum, CompetencyProgress as CompetencyProgressResponse } from 'app/openapi/model/competency-progress';
import type { CompetencyRelation, CompetencyRelationRelationTypeEnum } from 'app/openapi/model/competency-relation';
import type { CompetencyWithTailRelation } from 'app/openapi/model/competency-with-tail-relation';
import type { CourseCompetencyRequest } from 'app/openapi/model/course-competency-request';
import type { CourseCompetencyResponse, CourseCompetencyResponseTaxonomyEnum, CourseCompetencyResponseTypeEnum } from 'app/openapi/model/course-competency-response';
import type { CourseInfo } from 'app/openapi/model/course-info';
import type {
    ExerciseForCompetency,
    ExerciseForCompetencyAssessmentTypeEnum,
    ExerciseForCompetencyDifficultyEnum,
    ExerciseForCompetencyIncludedInOverallScoreEnum,
    ExerciseForCompetencyModeEnum,
    ExerciseForCompetencyTypeEnum,
} from 'app/openapi/model/exercise-for-competency';
import type { LectureUnitForCompetency } from 'app/openapi/model/lecture-unit-for-competency';

export type CompetencyProgressDTO = CompetencyProgressResponse;
export type CourseCompetencyResponseDTO = CourseCompetencyResponse;
export type CompetencyWithTailRelationResponseDTO = CompetencyWithTailRelation;

export interface CourseCompetencyProgressDTO {
    competencyId?: number;
    numberOfStudents?: number;
    numberOfMasteredStudents?: number;
    averageStudentScore?: number;
}

const taxonomyByValue: Record<CourseCompetencyResponseTaxonomyEnum, CompetencyTaxonomy> = {
    REMEMBER: CompetencyTaxonomy.REMEMBER,
    UNDERSTAND: CompetencyTaxonomy.UNDERSTAND,
    APPLY: CompetencyTaxonomy.APPLY,
    ANALYZE: CompetencyTaxonomy.ANALYZE,
    EVALUATE: CompetencyTaxonomy.EVALUATE,
    CREATE: CompetencyTaxonomy.CREATE,
};

const competencyTypeByValue: Record<CourseCompetencyResponseTypeEnum, CourseCompetencyType> = {
    competency: CourseCompetencyType.COMPETENCY,
    prerequisite: CourseCompetencyType.PREREQUISITE,
};

const confidenceReasonByValue: Record<CompetencyProgressConfidenceReasonEnum, ConfidenceReason> = {
    NO_REASON: ConfidenceReason.NO_REASON,
    RECENT_SCORES_LOWER: ConfidenceReason.RECENT_SCORES_LOWER,
    RECENT_SCORES_HIGHER: ConfidenceReason.RECENT_SCORES_HIGHER,
    MORE_EASY_POINTS: ConfidenceReason.MORE_EASY_POINTS,
    MORE_HARD_POINTS: ConfidenceReason.MORE_HARD_POINTS,
    QUICKLY_SOLVED_EXERCISES: ConfidenceReason.QUICKLY_SOLVED_EXERCISES,
    MORE_LOW_WEIGHTED_EXERCISES: ConfidenceReason.MORE_LOW_WEIGHTED_EXERCISES,
    MORE_HIGH_WEIGHTED_EXERCISES: ConfidenceReason.MORE_HIGH_WEIGHTED_EXERCISES,
};

const exerciseTypeByValue: Record<ExerciseForCompetencyTypeEnum, ExerciseType> = {
    programming: ExerciseType.PROGRAMMING,
    modeling: ExerciseType.MODELING,
    text: ExerciseType.TEXT,
    'file-upload': ExerciseType.FILE_UPLOAD,
    quiz: ExerciseType.QUIZ,
};

const assessmentTypeByValue: Record<ExerciseForCompetencyAssessmentTypeEnum, AssessmentType> = {
    AUTOMATIC: AssessmentType.AUTOMATIC,
    SEMI_AUTOMATIC: AssessmentType.SEMI_AUTOMATIC,
    MANUAL: AssessmentType.MANUAL,
    AUTOMATIC_ATHENA: AssessmentType.AUTOMATIC_ATHENA,
};

const difficultyByValue: Record<ExerciseForCompetencyDifficultyEnum, DifficultyLevel> = {
    EASY: DifficultyLevel.EASY,
    MEDIUM: DifficultyLevel.MEDIUM,
    HARD: DifficultyLevel.HARD,
};

const includedInOverallScoreByValue: Record<ExerciseForCompetencyIncludedInOverallScoreEnum, IncludedInOverallScore> = {
    INCLUDED_COMPLETELY: IncludedInOverallScore.INCLUDED_COMPLETELY,
    INCLUDED_AS_BONUS: IncludedInOverallScore.INCLUDED_AS_BONUS,
    NOT_INCLUDED: IncludedInOverallScore.NOT_INCLUDED,
};

const exerciseModeByValue: Record<ExerciseForCompetencyModeEnum, ExerciseMode> = {
    INDIVIDUAL: ExerciseMode.INDIVIDUAL,
    TEAM: ExerciseMode.TEAM,
};

const attachmentTypeByValue: Record<AttachmentForCompetencyAttachmentTypeEnum, AttachmentType> = {
    FILE: AttachmentType.FILE,
    URL: AttachmentType.URL,
};

const relationTypeByValue: Record<CompetencyRelationRelationTypeEnum, CompetencyRelationType> = {
    ASSUMES: CompetencyRelationType.ASSUMES,
    EXTENDS: CompetencyRelationType.EXTENDS,
    MATCHES: CompetencyRelationType.MATCHES,
};

const toCourse = (dto?: CourseInfo): Course | undefined => {
    if (!dto) {
        return undefined;
    }
    const course: Course = {
        id: dto.id,
        title: dto.title,
        semester: dto.semester,
    };
    return course;
};

const toExercise = (dto?: ExerciseForCompetency, course?: Course): Exercise | undefined => {
    if (!dto || !dto.type) {
        return undefined;
    }

    let exercise: Exercise;
    switch (exerciseTypeByValue[dto.type]) {
        case ExerciseType.PROGRAMMING:
            exercise = new ProgrammingExercise(course, undefined);
            break;
        case ExerciseType.MODELING:
            exercise = new ModelingExercise('ClassDiagram' satisfies UMLDiagramType, course, undefined);
            break;
        case ExerciseType.TEXT:
            exercise = new TextExercise(course, undefined);
            break;
        case ExerciseType.FILE_UPLOAD:
            exercise = new FileUploadExercise(course, undefined);
            break;
        case ExerciseType.QUIZ:
            exercise = new QuizExercise(course, undefined);
            break;
        default:
            captureException(new Error(`Unknown exercise type '${String(dto.type)}' for competency exercise mapping (id=${dto.id}); falling back to TextExercise.`));
            exercise = new TextExercise(course, undefined);
            break;
    }

    exercise.id = dto.id;
    exercise.title = dto.title;
    exercise.shortName = dto.shortName;
    exercise.releaseDate = convertDateStringFromServer(dto.releaseDate);
    exercise.startDate = convertDateStringFromServer(dto.startDate);
    exercise.dueDate = convertDateStringFromServer(dto.dueDate);
    exercise.assessmentDueDate = convertDateStringFromServer(dto.assessmentDueDate);
    exercise.maxPoints = dto.maxPoints;
    exercise.bonusPoints = dto.bonusPoints;
    exercise.assessmentType = dto.assessmentType ? assessmentTypeByValue[dto.assessmentType] : undefined;
    exercise.difficulty = dto.difficulty ? difficultyByValue[dto.difficulty] : undefined;
    exercise.includedInOverallScore = dto.includedInOverallScore ? includedInOverallScoreByValue[dto.includedInOverallScore] : undefined;
    exercise.mode = dto.mode ? exerciseModeByValue[dto.mode] : undefined;
    exercise.categories = dto.categories?.flatMap((value) => {
        try {
            const { category, color } = parseJson<SerializedExerciseCategory>(value);
            return [new ExerciseCategory(category, color)];
        } catch {
            return [];
        }
    });
    exercise.teamMode = dto.teamMode ?? exercise.mode === ExerciseMode.TEAM;
    exercise.studentAssignedTeamId = dto.studentAssignedTeamId;
    exercise.studentAssignedTeamIdComputed = dto.studentAssignedTeamIdComputed ?? false;

    if (exercise instanceof ProgrammingExercise) {
        exercise.allowOnlineEditor = dto.allowOnlineEditor;
        exercise.allowOfflineIde = dto.allowOfflineIde;
    }
    if (exercise instanceof QuizExercise) {
        exercise.quizStarted = dto.quizStarted;
        exercise.quizEnded = dto.quizEnded;
    }

    return exercise;
};

const toLectureUnit = (dto?: LectureUnitForCompetency): LectureUnit | undefined => {
    if (!dto?.type) {
        return undefined;
    }

    let lectureUnit: LectureUnit;
    switch (dto.type) {
        case LectureUnitType.ATTACHMENT_VIDEO: {
            const unit = new AttachmentVideoUnit();
            unit.description = dto.description;
            unit.videoSource = dto.videoSource;
            if (dto.attachment) {
                const attachment = new Attachment();
                attachment.id = dto.attachment.id;
                attachment.name = dto.attachment.name;
                attachment.link = dto.attachment.link;
                attachment.releaseDate = convertDateStringFromServer(dto.attachment.releaseDate);
                attachment.uploadDate = convertDateStringFromServer(dto.attachment.uploadDate);
                attachment.version = dto.attachment.version;
                attachment.attachmentType = dto.attachment.attachmentType ? attachmentTypeByValue[dto.attachment.attachmentType] : undefined;
                attachment.studentVersion = dto.attachment.studentVersion;
                unit.attachment = attachment;
            }
            lectureUnit = unit;
            break;
        }
        case LectureUnitType.ONLINE: {
            const unit = new OnlineUnit();
            unit.description = dto.description;
            unit.source = dto.source;
            lectureUnit = unit;
            break;
        }
        case LectureUnitType.TEXT: {
            const unit = new TextUnit();
            unit.content = dto.content;
            lectureUnit = unit;
            break;
        }
        case LectureUnitType.EXERCISE:
            lectureUnit = new ExerciseUnit();
            break;
        default:
            lectureUnit = new TextUnit();
            break;
    }

    lectureUnit.id = dto.id;
    lectureUnit.name = dto.name;
    lectureUnit.releaseDate = convertDateStringFromServer(dto.releaseDate);
    lectureUnit.completed = dto.completed;
    lectureUnit.visibleToStudents = dto.visibleToStudents;
    lectureUnit.lecture = dto.lecture ? { id: dto.lecture.id } : undefined;

    return lectureUnit;
};

const mapCourseCompetencyBase = <T extends CourseCompetency>(dto: CourseCompetencyResponse, competency: T): T => {
    competency.id = dto.id;
    competency.title = dto.title;
    competency.description = dto.description;
    competency.taxonomy = dto.taxonomy ? taxonomyByValue[dto.taxonomy] : undefined;
    competency.softDueDate = convertDateStringFromServer(dto.softDueDate);
    competency.masteryThreshold = dto.masteryThreshold;
    competency.optional = dto.optional;
    competency.generatedByAi = dto.generatedByAi ?? false;
    if (dto.type) {
        competency.type = competencyTypeByValue[dto.type];
    }
    if (dto.linkedStandardizedCompetencyId) {
        competency.linkedStandardizedCompetency = { id: dto.linkedStandardizedCompetencyId };
    }
    competency.userProgress = dto.userProgress?.map(toCompetencyProgress);

    if (dto.course) {
        competency.course = toCourse(dto.course);
    }

    if (dto.linkedCourseCompetency) {
        const linked = new Competency();
        linked.id = dto.linkedCourseCompetency.id;
        linked.course = {
            id: dto.linkedCourseCompetency.courseId,
            title: dto.linkedCourseCompetency.courseTitle,
            semester: dto.linkedCourseCompetency.semester,
        };
        competency.linkedCourseCompetency = linked;
    }

    if (dto.exerciseLinks) {
        competency.exerciseLinks = dto.exerciseLinks
            .map((linkDto) => {
                const exercise = toExercise(linkDto.exercise, competency.course);
                if (!exercise) {
                    return undefined;
                }
                return new CompetencyExerciseLink(competency, exercise, linkDto.weight, linkDto.generatedByAi ?? false);
            })
            .filter((link): link is CompetencyExerciseLink => !!link);
    }

    if (dto.lectureUnitLinks) {
        competency.lectureUnitLinks = dto.lectureUnitLinks
            .map((linkDto) => {
                const lectureUnit = toLectureUnit(linkDto.lectureUnit);
                if (!lectureUnit) {
                    return undefined;
                }
                return new CompetencyLectureUnitLink(competency, lectureUnit, linkDto.weight, linkDto.generatedByAi ?? false);
            })
            .filter((link): link is CompetencyLectureUnitLink => !!link);
    }

    return competency;
};

export const toCompetency = (dto: CourseCompetencyResponse): Competency => mapCourseCompetencyBase(dto, new Competency());

export const toPrerequisite = (dto: CourseCompetencyResponse): Prerequisite => mapCourseCompetencyBase(dto, new Prerequisite());

export const toCompetencyProgress = (dto: CompetencyProgressResponse): CompetencyProgress => ({
    progress: dto.progress,
    confidence: dto.confidence,
    confidenceReason: dto.confidenceReason ? confidenceReasonByValue[dto.confidenceReason] : undefined,
});

export const toCompetencyRelationDTO = (dto: CompetencyRelation): CompetencyRelationDTO => ({
    id: dto.id,
    tailCompetencyId: dto.tailCompetencyId,
    headCompetencyId: dto.headCompetencyId,
    relationType: relationTypeByValue[dto.relationType],
});

export const toCompetencyWithTailRelation = (
    dto: CompetencyWithTailRelation,
    hydrateCompetency: (competency: CourseCompetencyResponse) => CourseCompetency,
): CompetencyWithTailRelationDTO => {
    const result = new CompetencyWithTailRelationDTO();
    result.competency = hydrateCompetency(dto.competency);
    result.tailRelations = dto.tailRelations?.map(toCompetencyRelationDTO);
    return result;
};

/** Builds the editable request from the hydrated client model. */
export const toCourseCompetencyRequestDTO = (competency: CourseCompetency): CourseCompetencyRequest => {
    if (competency.masteryThreshold === undefined) {
        throw new Error('Cannot save a course competency without a mastery threshold');
    }
    return {
        id: competency.id,
        title: competency.title ?? '',
        description: competency.description,
        softDueDate: convertDateFromClient(competency.softDueDate),
        masteryThreshold: competency.masteryThreshold,
        taxonomy: competency.taxonomy,
        optional: competency.optional,
    };
};
