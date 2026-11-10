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
import { CompetencyLinkDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { parseJson } from 'app/foundation/util/json.util';
import {
    FileUploadExerciseAssessmentTypeEnum,
    FileUploadExerciseDifficultyEnum,
    FileUploadExerciseIncludedInOverallScoreEnum,
    FileUploadExerciseModeEnum,
    FileUploadExercise as FileUploadExerciseResponse,
} from 'app/openapi/model/file-upload-exercise';
import { FileUploadExerciseInput } from 'app/openapi/model/file-upload-exercise-input';
import { FileUploadTeamAssignmentConfig } from 'app/openapi/model/file-upload-team-assignment-config';
import { FileUploadPlagiarismDetectionConfig } from 'app/openapi/model/file-upload-plagiarism-detection-config';
import { FileUploadExerciseCourse, FileUploadExerciseCourseCourseInformationSharingConfigurationEnum } from 'app/openapi/model/file-upload-exercise-course';
import { FileUploadExerciseExamGroup } from 'app/openapi/model/file-upload-exercise-exam-group';
import { FileUploadExerciseExam } from 'app/openapi/model/file-upload-exercise-exam';
import type { ExerciseVariantGroupReference as GeneratedExerciseVariantGroupReference } from 'app/openapi/model/exercise-variant-group-reference';
import type { GradingCriterion as GeneratedGradingCriterion } from 'app/openapi/model/grading-criterion';
import type { GradingInstruction as GeneratedGradingInstruction } from 'app/openapi/model/grading-instruction';
import type { CompetencyLink as GeneratedCompetencyLink } from 'app/openapi/model/competency-link';
import type { Competency as GeneratedCompetency } from 'app/openapi/model/competency';

/** Every generated enum maps to the client enum of the same values; a missing entry fails to compile. */
const DIFFICULTY_LEVELS: Record<FileUploadExerciseDifficultyEnum, DifficultyLevel> = { EASY: DifficultyLevel.EASY, MEDIUM: DifficultyLevel.MEDIUM, HARD: DifficultyLevel.HARD };

const INCLUDED_IN_OVERALL_SCORE: Record<FileUploadExerciseIncludedInOverallScoreEnum, IncludedInOverallScore> = {
    INCLUDED_COMPLETELY: IncludedInOverallScore.INCLUDED_COMPLETELY,
    INCLUDED_AS_BONUS: IncludedInOverallScore.INCLUDED_AS_BONUS,
    NOT_INCLUDED: IncludedInOverallScore.NOT_INCLUDED,
};

const ASSESSMENT_TYPES: Record<FileUploadExerciseAssessmentTypeEnum, AssessmentType> = {
    AUTOMATIC: AssessmentType.AUTOMATIC,
    SEMI_AUTOMATIC: AssessmentType.SEMI_AUTOMATIC,
    MANUAL: AssessmentType.MANUAL,
    AUTOMATIC_ATHENA: AssessmentType.AUTOMATIC_ATHENA,
};

const EXERCISE_MODES: Record<FileUploadExerciseModeEnum, ExerciseMode> = { INDIVIDUAL: ExerciseMode.INDIVIDUAL, TEAM: ExerciseMode.TEAM };

const COURSE_INFORMATION_SHARING_CONFIGURATIONS: Record<FileUploadExerciseCourseCourseInformationSharingConfigurationEnum, CourseInformationSharingConfiguration> = {
    DISABLED: CourseInformationSharingConfiguration.DISABLED,
    COMMUNICATION_AND_MESSAGING: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    COMMUNICATION_ONLY: CourseInformationSharingConfiguration.COMMUNICATION_ONLY,
};

function mapEnum<K extends string, V>(values: Record<K, V>, value: K | undefined): V | undefined {
    return value === undefined ? undefined : values[value];
}

/** Converts the component-facing exercise into the scalar create/import wire contract. */
export function toFileUploadExerciseInputDTO(fileUploadExercise: FileUploadExercise): FileUploadExerciseInput {
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
        presentationScoreEnabled: fileUploadExercise.presentationScoreEnabled,
        secondCorrectionEnabled: fileUploadExercise.secondCorrectionEnabled,
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
export function fromFileUploadExerciseDTO(dto: FileUploadExerciseResponse): FileUploadExercise {
    const course = dto.course ? toCourse(dto.course) : undefined;
    const exerciseGroup = dto.exerciseGroup ? toExerciseGroup(dto.exerciseGroup) : undefined;
    const exercise = new FileUploadExercise(course, exerciseGroup);

    exercise.id = dto.id;
    // The server always sends file-upload here; the class keeps the client enum.
    exercise.type = ExerciseType.FILE_UPLOAD;
    exercise.title = dto.title;
    exercise.channelName = dto.channelName;
    exercise.shortName = dto.shortName;
    exercise.problemStatement = dto.problemStatement;
    exercise.categories = dto.categories?.map(toExerciseCategory).filter((category): category is ExerciseCategory => category !== undefined);
    exercise.difficulty = mapEnum(DIFFICULTY_LEVELS, dto.difficulty);
    exercise.maxPoints = dto.maxPoints;
    exercise.bonusPoints = dto.bonusPoints;
    exercise.includedInOverallScore = mapEnum(INCLUDED_IN_OVERALL_SCORE, dto.includedInOverallScore);
    exercise.assessmentType = mapEnum(ASSESSMENT_TYPES, dto.assessmentType);
    exercise.mode = mapEnum(EXERCISE_MODES, dto.mode);
    exercise.teamMode = dto.teamMode;
    exercise.teamAssignmentConfig = dto.teamAssignmentConfig ? fromTeamAssignmentConfigDTO(dto.teamAssignmentConfig) : undefined;
    exercise.allowComplaintsForAutomaticAssessments = dto.allowComplaintsForAutomaticAssessments;
    exercise.presentationScoreEnabled = dto.presentationScoreEnabled;
    exercise.secondCorrectionEnabled = dto.secondCorrectionEnabled ?? false;
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

function fromTeamAssignmentConfigDTO(dto: FileUploadTeamAssignmentConfig): TeamAssignmentConfig {
    const config = new TeamAssignmentConfig();
    config.id = dto.id;
    config.minTeamSize = dto.minTeamSize;
    config.maxTeamSize = dto.maxTeamSize;
    return config;
}

function fromGradingCriterionDTO(dto: GeneratedGradingCriterion): GradingCriterion {
    const criterion = new GradingCriterion();
    criterion.id = dto.id;
    criterion.title = dto.title ?? '';
    criterion.structuredGradingInstructions = (dto.structuredGradingInstructions ?? []).map(fromGradingInstructionDTO);
    return criterion;
}

function fromGradingInstructionDTO(dto: GeneratedGradingInstruction): GradingInstruction {
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

function fromCompetencyLinkDTO(dto: GeneratedCompetencyLink, exercise: FileUploadExercise): CompetencyExerciseLink {
    return new CompetencyExerciseLink(dto.competency ? fromCompetencyDTO(dto.competency) : undefined, exercise, dto.weight);
}

function fromCompetencyDTO(dto: GeneratedCompetency): Competency {
    const competency = new Competency();
    competency.id = dto.id;
    competency.title = dto.title;
    return competency;
}

function toTeamAssignmentConfigDTO(config?: TeamAssignmentConfig): FileUploadTeamAssignmentConfig | undefined {
    if (!config) {
        return undefined;
    }
    return { id: config.id, minTeamSize: config.minTeamSize, maxTeamSize: config.maxTeamSize };
}

function toPlagiarismDetectionConfigDTO(config?: PlagiarismDetectionConfig): FileUploadPlagiarismDetectionConfig | undefined {
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

function toCourse(dto: FileUploadExerciseCourse): Course {
    const course = new Course();
    course.id = dto.id;
    course.title = dto.title;
    course.shortName = dto.shortName;
    course.testCourse = dto.testCourse;
    course.presentationScore = dto.presentationScore;
    course.courseInformationSharingConfiguration = mapEnum(COURSE_INFORMATION_SHARING_CONFIGURATIONS, dto.courseInformationSharingConfiguration);
    course.accuracyOfScores = dto.accuracyOfScores;
    return course;
}

function toExerciseGroup(dto: FileUploadExerciseExamGroup): ExerciseGroup {
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.id = dto.id;
    exerciseGroup.exam = dto.exam ? toExam(dto.exam) : undefined;
    return exerciseGroup;
}

function toExerciseVariantGroup(dto: GeneratedExerciseVariantGroupReference): ExerciseVariantGroupReference {
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

function toExam(dto: FileUploadExerciseExam): Exam {
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
