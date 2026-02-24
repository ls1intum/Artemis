import {
    Competency,
    CompetencyExerciseLink,
    CompetencyLectureUnitLink,
    CompetencyProgress,
    CompetencyRelationDTO,
    CompetencyTaxonomy,
    ConfidenceReason,
    CourseCompetency,
    CourseCompetencyType,
} from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { StandardizedCompetency } from 'app/atlas/shared/entities/standardized-competency.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DifficultyLevel, Exercise, ExerciseMode, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { UMLDiagramType as UMLDiagramTypes } from '@ls1intum/apollon';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/shared/util/date.utils';

export interface CourseCompetencyRequestDTO {
    id?: number;
    title: string;
    description?: string;
    softDueDate?: string;
    masteryThreshold?: number;
    taxonomy?: CompetencyTaxonomy;
    optional?: boolean;
}

export interface CompetencyProgressDTO {
    progress?: number;
    confidence?: number;
    confidenceReason?: ConfidenceReason;
}

export interface CourseInfoDTO {
    id: number;
    title?: string;
    semester?: string;
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    editorGroupName?: string;
    instructorGroupName?: string;
}

export interface LinkedCourseCompetencyDTO {
    id: number;
    courseId: number;
    courseTitle?: string;
    semester?: string;
}

export interface ExerciseForCompetencyDTO {
    id: number;
    title?: string;
    shortName?: string;
    type?: ExerciseType;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    maxPoints?: number;
    bonusPoints?: number;
    assessmentType?: AssessmentType;
    difficulty?: DifficultyLevel;
    includedInOverallScore?: IncludedInOverallScore;
    mode?: ExerciseMode;
    categories?: (ExerciseCategory | string)[];
    teamMode?: boolean;
    studentAssignedTeamId?: number;
    studentAssignedTeamIdComputed?: boolean;
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    quizStarted?: boolean;
    quizEnded?: boolean;
}

export interface CompetencyExerciseLinkResponseDTO {
    weight: number;
    exercise?: ExerciseForCompetencyDTO;
}

export interface LectureReferenceDTO {
    id: number;
}

export interface AttachmentForCompetencyDTO {
    id?: number;
    name?: string;
    link?: string;
    releaseDate?: string;
    uploadDate?: string;
    version?: number;
    attachmentType?: AttachmentType;
    studentVersion?: string;
}

export interface LectureUnitForCompetencyDTO {
    id?: number;
    lecture?: LectureReferenceDTO;
    name?: string;
    releaseDate?: string;
    completed?: boolean;
    visibleToStudents?: boolean;
    type?: LectureUnitType;
    description?: string;
    source?: string;
    content?: string;
    attachment?: AttachmentForCompetencyDTO;
    videoSource?: string;
}

export interface CompetencyLectureUnitLinkResponseDTO {
    weight: number;
    lectureUnit?: LectureUnitForCompetencyDTO;
}

export interface CourseCompetencyResponseDTO {
    id: number;
    title: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
    softDueDate?: string;
    masteryThreshold?: number;
    optional?: boolean;
    type?: CourseCompetencyType;
    linkedCourseCompetency?: LinkedCourseCompetencyDTO;
    linkedStandardizedCompetencyId?: number;
    userProgress?: CompetencyProgressDTO[];
    course?: CourseInfoDTO;
    exerciseLinks?: CompetencyExerciseLinkResponseDTO[];
    lectureUnitLinks?: CompetencyLectureUnitLinkResponseDTO[];
}

export interface CompetencyWithTailRelationResponseDTO {
    competency?: CourseCompetencyResponseDTO;
    tailRelations?: CompetencyRelationDTO[];
}

const toCourse = (dto?: CourseInfoDTO): Course | undefined => {
    if (!dto) {
        return undefined;
    }
    return {
        id: dto.id,
        title: dto.title,
        semester: dto.semester,
        studentGroupName: dto.studentGroupName,
        teachingAssistantGroupName: dto.teachingAssistantGroupName,
        editorGroupName: dto.editorGroupName,
        instructorGroupName: dto.instructorGroupName,
    } as Course;
};

const toExercise = (dto?: ExerciseForCompetencyDTO, course?: Course): Exercise | undefined => {
    if (!dto || !dto.type) {
        return undefined;
    }

    let exercise: Exercise;
    switch (dto.type) {
        case ExerciseType.PROGRAMMING:
            exercise = new ProgrammingExercise(course, undefined);
            break;
        case ExerciseType.MODELING:
            exercise = new ModelingExercise(UMLDiagramTypes.ClassDiagram, course, undefined);
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
            exercise = new TextExercise(course, undefined);
            break;
    }

    exercise.id = dto.id;
    exercise.title = dto.title;
    exercise.shortName = dto.shortName;
    exercise.type = dto.type;
    exercise.releaseDate = convertDateStringFromServer(dto.releaseDate);
    exercise.startDate = convertDateStringFromServer(dto.startDate);
    exercise.dueDate = convertDateStringFromServer(dto.dueDate);
    exercise.assessmentDueDate = convertDateStringFromServer(dto.assessmentDueDate);
    exercise.maxPoints = dto.maxPoints;
    exercise.bonusPoints = dto.bonusPoints;
    exercise.assessmentType = dto.assessmentType;
    exercise.difficulty = dto.difficulty;
    exercise.includedInOverallScore = dto.includedInOverallScore;
    exercise.mode = dto.mode;
    exercise.categories = dto.categories as ExerciseCategory[] | undefined;
    exercise.teamMode = dto.teamMode ?? dto.mode === ExerciseMode.TEAM;
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

const toLectureUnit = (dto?: LectureUnitForCompetencyDTO): LectureUnit | undefined => {
    if (!dto?.type) {
        return undefined;
    }

    let lectureUnit: LectureUnit;
    switch (dto.type) {
        case LectureUnitType.ATTACHMENT_VIDEO:
            lectureUnit = new AttachmentVideoUnit();
            (lectureUnit as AttachmentVideoUnit).description = dto.description;
            (lectureUnit as AttachmentVideoUnit).videoSource = dto.videoSource;
            if (dto.attachment) {
                const attachment = new Attachment();
                attachment.id = dto.attachment.id;
                attachment.name = dto.attachment.name;
                attachment.link = dto.attachment.link;
                attachment.releaseDate = convertDateStringFromServer(dto.attachment.releaseDate);
                attachment.uploadDate = convertDateStringFromServer(dto.attachment.uploadDate);
                attachment.version = dto.attachment.version;
                attachment.attachmentType = dto.attachment.attachmentType;
                attachment.studentVersion = dto.attachment.studentVersion;
                (lectureUnit as AttachmentVideoUnit).attachment = attachment;
            }
            break;
        case LectureUnitType.ONLINE:
            lectureUnit = new OnlineUnit();
            (lectureUnit as OnlineUnit).description = dto.description;
            (lectureUnit as OnlineUnit).source = dto.source;
            break;
        case LectureUnitType.TEXT:
            lectureUnit = new TextUnit();
            (lectureUnit as TextUnit).content = dto.content;
            break;
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
    lectureUnit.type = dto.type;
    lectureUnit.lecture = dto.lecture ? ({ id: dto.lecture.id } as Lecture) : undefined;

    return lectureUnit;
};

const mapCourseCompetencyBase = <T extends CourseCompetency>(dto: CourseCompetencyResponseDTO, competency: T): T => {
    competency.id = dto.id;
    competency.title = dto.title;
    competency.description = dto.description;
    competency.taxonomy = dto.taxonomy;
    competency.softDueDate = convertDateStringFromServer(dto.softDueDate);
    competency.masteryThreshold = dto.masteryThreshold;
    competency.optional = dto.optional;
    if (dto.type) {
        competency.type = dto.type;
    }
    if (dto.linkedStandardizedCompetencyId) {
        competency.linkedStandardizedCompetency = { id: dto.linkedStandardizedCompetencyId } as StandardizedCompetency;
    }
    competency.userProgress = dto.userProgress?.map((progress) => ({
        progress: progress.progress,
        confidence: progress.confidence,
        confidenceReason: progress.confidenceReason,
    })) as CompetencyProgress[] | undefined;

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
        } as Course;
        competency.linkedCourseCompetency = linked;
    }

    if (dto.exerciseLinks) {
        competency.exerciseLinks = dto.exerciseLinks
            .map((linkDto) => {
                const exercise = toExercise(linkDto.exercise, competency.course);
                if (!exercise) {
                    return undefined;
                }
                return new CompetencyExerciseLink(competency, exercise, linkDto.weight);
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
                return new CompetencyLectureUnitLink(competency, lectureUnit, linkDto.weight);
            })
            .filter((link): link is CompetencyLectureUnitLink => !!link);
    }

    return competency;
};

export const toCompetency = (dto: CourseCompetencyResponseDTO): Competency => mapCourseCompetencyBase(dto, new Competency());

export const toPrerequisite = (dto: CourseCompetencyResponseDTO): Prerequisite => mapCourseCompetencyBase(dto, new Prerequisite());

export const toCourseCompetencyRequestDTO = (competency: CourseCompetency): CourseCompetencyRequestDTO => {
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
