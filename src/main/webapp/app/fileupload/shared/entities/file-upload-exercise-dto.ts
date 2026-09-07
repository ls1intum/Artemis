import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import {
    DifficultyLevel,
    ExerciseMode,
    ExerciseType,
    ExerciseVariantGroupReference,
    IncludedInOverallScore,
    PlagiarismDetectionConfig,
} from 'app/exercise/shared/entities/exercise/exercise.model';
import { CompetencyDTO, CompetencyLinkDTO, GradingCriterionDTO, GradingInstructionDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { parseJson } from 'app/foundation/util/json.util';

export interface FileUploadTeamAssignmentConfigDto {
    id?: number;
    minTeamSize?: number;
    maxTeamSize?: number;
}

export type FileUploadPlagiarismDetectionConfigDto = PlagiarismDetectionConfig;

export interface CourseContextDto {
    id: number;
    title?: string;
    shortName?: string;
    testCourse?: boolean;
    presentationScore?: number;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
    accuracyOfScores?: number;
}

export interface ExamContextDto {
    id: number;
    title?: string;
    course?: CourseContextDto;
    startDate?: string;
    endDate?: string;
    exampleSolutionPublicationDate?: string;
    numberOfCorrectionRoundsInExam?: number;
}

export interface ExerciseGroupContextDto {
    id: number;
    exam?: ExamContextDto;
}

export interface ExerciseVariantGroupReferenceDto {
    id?: number;
    title?: string;
    maxPoints?: number;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
}

export interface FileUploadExerciseDto {
    id: number;
    type: ExerciseType.FILE_UPLOAD;
    title?: string;
    channelName?: string;
    shortName?: string;
    problemStatement?: string;
    categories?: string[];
    difficulty?: DifficultyLevel;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
    assessmentType?: AssessmentType;
    mode?: ExerciseMode;
    teamMode: boolean;
    teamAssignmentConfig?: FileUploadTeamAssignmentConfigDto;
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;
    feedbackSuggestionModule?: string;
    gradingInstructions?: string;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
    exampleSolution?: string;
    filePattern?: string;
    gradingInstructionFeedbackUsed: boolean;
    course?: CourseContextDto;
    exerciseGroup?: ExerciseGroupContextDto;
    exerciseVariantGroup?: ExerciseVariantGroupReferenceDto;
    gradingCriteria?: GradingCriterionDTO[];
    competencyLinks?: CompetencyLinkDTO[];
    plagiarismDetectionConfig?: FileUploadPlagiarismDetectionConfigDto;
}

export interface FileUploadExerciseInputDto {
    id?: number;
    title?: string;
    channelName?: string;
    shortName?: string;
    problemStatement?: string;
    categories?: string[];
    difficulty?: DifficultyLevel;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
    mode?: ExerciseMode;
    teamAssignmentConfig?: FileUploadTeamAssignmentConfigDto;
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;
    feedbackSuggestionModule?: string;
    gradingInstructions?: string;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
    exampleSolution?: string;
    filePattern?: string;
    courseId?: number;
    exerciseGroupId?: number;
    gradingCriteria?: GradingCriterionDTO[];
    competencyLinks?: CompetencyLinkDTO[];
    plagiarismDetectionConfig?: FileUploadPlagiarismDetectionConfigDto;
}

/** Converts the component-facing exercise into the scalar create/import wire contract. */
export function toFileUploadExerciseInputDTO(fileUploadExercise: FileUploadExercise): FileUploadExerciseInputDto {
    return {
        id: fileUploadExercise.id,
        title: fileUploadExercise.title,
        channelName: fileUploadExercise.channelName,
        shortName: fileUploadExercise.shortName,
        problemStatement: fileUploadExercise.problemStatement,
        categories: fileUploadExercise.categories?.map((category) => JSON.stringify(category)),
        difficulty: fileUploadExercise.difficulty,
        maxPoints: fileUploadExercise.maxPoints,
        bonusPoints: fileUploadExercise.bonusPoints,
        includedInOverallScore: fileUploadExercise.includedInOverallScore,
        mode: fileUploadExercise.mode,
        teamAssignmentConfig: toTeamAssignmentConfigDTO(fileUploadExercise.teamAssignmentConfig),
        allowComplaintsForAutomaticAssessments: fileUploadExercise.allowComplaintsForAutomaticAssessments,
        allowFeedbackRequests: fileUploadExercise.allowFeedbackRequests,
        presentationScoreEnabled: fileUploadExercise.presentationScoreEnabled,
        secondCorrectionEnabled: fileUploadExercise.secondCorrectionEnabled,
        feedbackSuggestionModule: fileUploadExercise.feedbackSuggestionModule,
        gradingInstructions: fileUploadExercise.gradingInstructions,
        releaseDate: convertDateFromClient(fileUploadExercise.releaseDate),
        startDate: convertDateFromClient(fileUploadExercise.startDate),
        dueDate: convertDateFromClient(fileUploadExercise.dueDate),
        assessmentDueDate: convertDateFromClient(fileUploadExercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(fileUploadExercise.exampleSolutionPublicationDate),
        exampleSolution: fileUploadExercise.exampleSolution,
        filePattern: fileUploadExercise.filePattern,
        courseId: fileUploadExercise.course?.id,
        exerciseGroupId: fileUploadExercise.exerciseGroup?.id,
        gradingCriteria: fileUploadExercise.gradingCriteria,
        competencyLinks: (fileUploadExercise.competencyLinks ?? []).map((link) => toCompetencyLinkDTO(link, 1)),
        plagiarismDetectionConfig: toPlagiarismDetectionConfigDTO(fileUploadExercise.plagiarismDetectionConfig),
    };
}

/** Rebuilds the component-facing model and its minimal course/exam context from a response DTO. */
export function fromFileUploadExerciseDTO(dto: FileUploadExerciseDto): FileUploadExercise {
    const course = dto.course ? toCourse(dto.course) : undefined;
    const exerciseGroup = dto.exerciseGroup ? toExerciseGroup(dto.exerciseGroup) : undefined;
    const exercise = new FileUploadExercise(course, exerciseGroup);

    exercise.id = dto.id;
    exercise.type = dto.type;
    exercise.title = dto.title;
    exercise.channelName = dto.channelName;
    exercise.shortName = dto.shortName;
    exercise.problemStatement = dto.problemStatement;
    exercise.categories = dto.categories?.map(toExerciseCategory).filter((category): category is ExerciseCategory => category !== undefined);
    exercise.difficulty = dto.difficulty;
    exercise.maxPoints = dto.maxPoints;
    exercise.bonusPoints = dto.bonusPoints;
    exercise.includedInOverallScore = dto.includedInOverallScore;
    exercise.assessmentType = dto.assessmentType;
    exercise.mode = dto.mode;
    exercise.teamMode = dto.teamMode;
    exercise.teamAssignmentConfig = dto.teamAssignmentConfig ? fromTeamAssignmentConfigDTO(dto.teamAssignmentConfig) : undefined;
    exercise.allowComplaintsForAutomaticAssessments = dto.allowComplaintsForAutomaticAssessments;
    exercise.allowFeedbackRequests = dto.allowFeedbackRequests;
    exercise.presentationScoreEnabled = dto.presentationScoreEnabled;
    exercise.secondCorrectionEnabled = dto.secondCorrectionEnabled ?? false;
    exercise.feedbackSuggestionModule = dto.feedbackSuggestionModule;
    exercise.gradingInstructions = dto.gradingInstructions;
    exercise.releaseDate = convertDateStringFromServer(dto.releaseDate);
    exercise.startDate = convertDateStringFromServer(dto.startDate);
    exercise.dueDate = convertDateStringFromServer(dto.dueDate);
    exercise.assessmentDueDate = convertDateStringFromServer(dto.assessmentDueDate);
    exercise.exampleSolutionPublicationDate = convertDateStringFromServer(dto.exampleSolutionPublicationDate);
    exercise.exampleSolution = dto.exampleSolution;
    exercise.filePattern = dto.filePattern;
    exercise.gradingInstructionFeedbackUsed = dto.gradingInstructionFeedbackUsed;
    exercise.exerciseVariantGroup = dto.exerciseVariantGroup ? toExerciseVariantGroup(dto.exerciseVariantGroup) : undefined;
    exercise.gradingCriteria = dto.gradingCriteria?.map(fromGradingCriterionDTO);
    exercise.competencyLinks = dto.competencyLinks?.map((link) => fromCompetencyLinkDTO(link, exercise));
    exercise.plagiarismDetectionConfig = dto.plagiarismDetectionConfig ? deepClone(dto.plagiarismDetectionConfig) : undefined;
    return exercise;
}

function fromTeamAssignmentConfigDTO(dto: FileUploadTeamAssignmentConfigDto): TeamAssignmentConfig {
    const config = new TeamAssignmentConfig();
    config.id = dto.id;
    config.minTeamSize = dto.minTeamSize;
    config.maxTeamSize = dto.maxTeamSize;
    return config;
}

function fromGradingCriterionDTO(dto: GradingCriterionDTO): GradingCriterion {
    const criterion = new GradingCriterion();
    criterion.id = dto.id;
    criterion.title = dto.title ?? '';
    criterion.structuredGradingInstructions = (dto.structuredGradingInstructions ?? []).map(fromGradingInstructionDTO);
    return criterion;
}

function fromGradingInstructionDTO(dto: GradingInstructionDTO): GradingInstruction {
    const instruction = new GradingInstruction();
    instruction.id = dto.id;
    if (dto.credits !== undefined) {
        instruction.credits = dto.credits;
    }
    if (dto.gradingScale !== undefined) {
        instruction.gradingScale = dto.gradingScale;
    }
    if (dto.instructionDescription !== undefined) {
        instruction.instructionDescription = dto.instructionDescription;
    }
    if (dto.feedback !== undefined) {
        instruction.feedback = dto.feedback;
    }
    instruction.usageCount = dto.usageCount;
    return instruction;
}

function fromCompetencyLinkDTO(dto: CompetencyLinkDTO, exercise: FileUploadExercise): CompetencyExerciseLink {
    return new CompetencyExerciseLink(fromCompetencyDTO(dto.competency), exercise, dto.weight);
}

function fromCompetencyDTO(dto: CompetencyDTO): Competency {
    const competency = new Competency();
    competency.id = dto.id;
    competency.title = dto.title;
    return competency;
}

function toTeamAssignmentConfigDTO(config?: TeamAssignmentConfig): FileUploadTeamAssignmentConfigDto | undefined {
    if (!config) {
        return undefined;
    }
    return { id: config.id, minTeamSize: config.minTeamSize, maxTeamSize: config.maxTeamSize };
}

function toPlagiarismDetectionConfigDTO(config?: PlagiarismDetectionConfig): FileUploadPlagiarismDetectionConfigDto | undefined {
    if (!config) {
        return undefined;
    }
    return {
        continuousPlagiarismControlEnabled: config.continuousPlagiarismControlEnabled,
        continuousPlagiarismControlPostDueDateChecksEnabled: config.continuousPlagiarismControlPostDueDateChecksEnabled,
        continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: config.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod,
        similarityThreshold: config.similarityThreshold,
        minimumScore: config.minimumScore,
        minimumSize: config.minimumSize,
    };
}

function toCourse(dto: CourseContextDto): Course {
    const course = new Course();
    course.id = dto.id;
    course.title = dto.title;
    course.shortName = dto.shortName;
    course.testCourse = dto.testCourse;
    course.presentationScore = dto.presentationScore;
    course.courseInformationSharingConfiguration = dto.courseInformationSharingConfiguration;
    course.accuracyOfScores = dto.accuracyOfScores;
    return course;
}

function toExerciseGroup(dto: ExerciseGroupContextDto): ExerciseGroup {
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.id = dto.id;
    exerciseGroup.exam = dto.exam ? toExam(dto.exam) : undefined;
    return exerciseGroup;
}

function toExerciseVariantGroup(dto: ExerciseVariantGroupReferenceDto): ExerciseVariantGroupReference {
    return {
        id: dto.id,
        title: dto.title,
        maxPoints: dto.maxPoints,
        releaseDate: convertDateStringFromServer(dto.releaseDate),
        startDate: convertDateStringFromServer(dto.startDate),
        dueDate: convertDateStringFromServer(dto.dueDate),
        assessmentDueDate: convertDateStringFromServer(dto.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateStringFromServer(dto.exampleSolutionPublicationDate),
    };
}

function toExam(dto: ExamContextDto): Exam {
    const exam = new Exam();
    exam.id = dto.id;
    exam.title = dto.title;
    exam.course = dto.course ? toCourse(dto.course) : undefined;
    exam.startDate = convertDateStringFromServer(dto.startDate);
    exam.endDate = convertDateStringFromServer(dto.endDate);
    exam.exampleSolutionPublicationDate = convertDateStringFromServer(dto.exampleSolutionPublicationDate);
    exam.numberOfCorrectionRoundsInExam = dto.numberOfCorrectionRoundsInExam;
    return exam;
}

function toExerciseCategory(category: string): ExerciseCategory | undefined {
    try {
        const parsed = parseJson<{ category: string; color: string }>(category);
        return new ExerciseCategory(parsed.category, parsed.color);
    } catch {
        return undefined;
    }
}

export function toCompetencyLinkDTO(link: CompetencyExerciseLink, fallbackWeight?: number): CompetencyLinkDTO {
    const competencyId = link.competency?.id;
    if (competencyId === undefined) {
        throw new Error('Cannot create a file upload exercise request with a competency link that has no competency ID');
    }
    const weight = link.weight ?? fallbackWeight;
    if (weight === undefined) {
        throw new Error('Cannot create a file upload exercise request with a competency link that has no weight');
    }
    return {
        competency: { id: competencyId },
        weight,
    };
}
