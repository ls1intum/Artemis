import { User, UserPublicInfoDTO } from 'app/account/user/user.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { ExerciseCategory, SerializedExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { DifficultyLevel, Exercise, ExerciseMode, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import {
    LockRepositoryPolicy,
    SubmissionPenaltyPolicy,
    SubmissionPolicy,
    SubmissionPolicyType,
} from 'app/exercise/shared/entities/submission/submission-policy.model';
import { SubmissionResponseDTO, fromSubmissionResponseDTO } from 'app/exercise/shared/entities/submission/submission-response.dto';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { parseJson } from 'app/foundation/util/json.util';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { UMLDiagramType } from '@tumaet/apollon';

export interface ExerciseDetailsCourseDTO {
    id: number;
    title?: string;
    shortName?: string;
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    editorGroupName?: string;
    instructorGroupName?: string;
    accuracyOfScores?: number;
    complaintsEnabled?: boolean;
    requestMoreFeedbackEnabled?: boolean;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
}

export interface ExerciseDetailsSubmissionPolicyDTO {
    id: number;
    type: SubmissionPolicyType.LOCK_REPOSITORY | SubmissionPolicyType.SUBMISSION_PENALTY;
    submissionLimit?: number;
    active?: boolean;
    exceedingPenalty?: number;
}

export interface ExerciseDetailsTeamDTO {
    id: number;
    name?: string;
    shortName?: string;
    students?: UserPublicInfoDTO[];
}

export interface ExerciseDetailsParticipationDTO {
    id: number;
    type: ParticipationType.STUDENT | ParticipationType.PROGRAMMING;
    testRun: boolean;
    initializationState?: InitializationState;
    initializationDate?: string;
    individualDueDate?: string;
    presentationScore?: number;
    student?: UserPublicInfoDTO;
    team?: ExerciseDetailsTeamDTO;
    submissions?: SubmissionResponseDTO[];
}

export interface ExerciseDetailsQuizBatchDTO {
    id: number;
    startTime?: string;
    started?: boolean;
    ended?: boolean;
}

export interface ExerciseDetailsResponseDTO {
    id: number;
    type: ExerciseType;
    exerciseType: ExerciseType;
    title?: string;
    shortName?: string;
    problemStatement?: string;
    gradingInstructions?: string;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
    maxPoints?: number;
    bonusPoints?: number;
    assessmentType?: AssessmentType;
    difficulty?: DifficultyLevel;
    mode: ExerciseMode;
    includedInOverallScore: IncludedInOverallScore;
    allowComplaintsForAutomaticAssessments: boolean;
    allowFeedbackRequests: boolean;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled: boolean;
    feedbackSuggestionModule?: string;
    categories?: string[];
    teamMode: boolean;
    studentAssignedTeamId?: number;
    studentAssignedTeamIdComputed: boolean;
    course?: ExerciseDetailsCourseDTO;
    studentParticipations?: ExerciseDetailsParticipationDTO[];
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    allowOnlineIde?: boolean;
    staticCodeAnalysisEnabled?: boolean;
    showTestNamesToStudents?: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: string;
    releaseTestsWithExampleSolution?: boolean;
    submissionPolicy?: ExerciseDetailsSubmissionPolicyDTO;
    visibleToStudents?: boolean;
    randomizeQuestionOrder?: boolean;
    allowedNumberOfAttempts?: number;
    remainingNumberOfAttempts?: number;
    quizMode?: QuizMode;
    duration?: number;
    quizBatches?: ExerciseDetailsQuizBatchDTO[];
    quizStarted?: boolean;
    quizEnded?: boolean;
    exampleSolution?: string;
    filePattern?: string;
    diagramType?: UMLDiagramType;
    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;
}

/**
 * Converts the explicit exercise-details wire DTO into the existing component-facing exercise subtype and reconnects its relationships.
 */
export function fromExerciseDetailsResponseDTO(dto: ExerciseDetailsResponseDTO): Exercise {
    const course = dto.course ? fromExerciseDetailsCourseDTO(dto.course) : undefined;
    const exercise = createExercise(dto, course);

    exercise.id = dto.id;
    exercise.title = dto.title;
    exercise.shortName = dto.shortName;
    exercise.problemStatement = dto.problemStatement;
    exercise.gradingInstructions = dto.gradingInstructions;
    exercise.releaseDate = convertDateStringFromServer(dto.releaseDate);
    exercise.startDate = convertDateStringFromServer(dto.startDate);
    exercise.dueDate = convertDateStringFromServer(dto.dueDate);
    exercise.assessmentDueDate = convertDateStringFromServer(dto.assessmentDueDate);
    exercise.exampleSolutionPublicationDate = convertDateStringFromServer(dto.exampleSolutionPublicationDate);
    exercise.maxPoints = dto.maxPoints;
    exercise.bonusPoints = dto.bonusPoints;
    exercise.assessmentType = dto.assessmentType;
    exercise.difficulty = dto.difficulty;
    exercise.mode = dto.mode;
    exercise.includedInOverallScore = dto.includedInOverallScore;
    exercise.allowComplaintsForAutomaticAssessments = dto.allowComplaintsForAutomaticAssessments;
    exercise.allowFeedbackRequests = dto.allowFeedbackRequests;
    exercise.presentationScoreEnabled = dto.presentationScoreEnabled;
    exercise.secondCorrectionEnabled = dto.secondCorrectionEnabled;
    exercise.feedbackSuggestionModule = dto.feedbackSuggestionModule;
    exercise.categories = dto.categories?.map(fromSerializedCategory).filter((category): category is ExerciseCategory => category !== undefined);
    exercise.teamMode = dto.teamMode;
    exercise.studentAssignedTeamId = dto.studentAssignedTeamId;
    exercise.studentAssignedTeamIdComputed = dto.studentAssignedTeamIdComputed;
    exercise.course = course;

    if (exercise instanceof ProgrammingExercise) {
        exercise.allowOnlineEditor = dto.allowOnlineEditor;
        exercise.allowOfflineIde = dto.allowOfflineIde;
        exercise.allowOnlineIde = dto.allowOnlineIde;
        exercise.staticCodeAnalysisEnabled = dto.staticCodeAnalysisEnabled;
        exercise.showTestNamesToStudents = dto.showTestNamesToStudents;
        exercise.buildAndTestStudentSubmissionsAfterDueDate = convertDateStringFromServer(dto.buildAndTestStudentSubmissionsAfterDueDate);
        exercise.releaseTestsWithExampleSolution = dto.releaseTestsWithExampleSolution;
        exercise.submissionPolicy = dto.submissionPolicy ? fromExerciseDetailsSubmissionPolicyDTO(dto.submissionPolicy) : undefined;
    } else if (exercise instanceof QuizExercise) {
        exercise.visibleToStudents = dto.visibleToStudents;
        exercise.randomizeQuestionOrder = dto.randomizeQuestionOrder;
        exercise.allowedNumberOfAttempts = dto.allowedNumberOfAttempts;
        exercise.remainingNumberOfAttempts = dto.remainingNumberOfAttempts;
        exercise.quizMode = dto.quizMode;
        exercise.duration = dto.duration;
        exercise.quizBatches = dto.quizBatches?.map(fromExerciseDetailsQuizBatchDTO);
        exercise.quizStarted = dto.quizStarted;
        exercise.quizEnded = dto.quizEnded;
    } else if (exercise instanceof TextExercise) {
        exercise.exampleSolution = dto.exampleSolution;
    } else if (exercise instanceof FileUploadExercise) {
        exercise.exampleSolution = dto.exampleSolution;
        exercise.filePattern = dto.filePattern;
    } else if (exercise instanceof ModelingExercise) {
        exercise.exampleSolutionModel = dto.exampleSolutionModel;
        exercise.exampleSolutionExplanation = dto.exampleSolutionExplanation;
    }

    exercise.studentParticipations = dto.studentParticipations?.map((participation) => fromExerciseDetailsParticipationDTO(participation, exercise));
    return exercise;
}

function createExercise(dto: ExerciseDetailsResponseDTO, course: Course | undefined): Exercise {
    switch (dto.type) {
        case ExerciseType.PROGRAMMING:
            return new ProgrammingExercise(course, undefined);
        case ExerciseType.QUIZ:
            return new QuizExercise(course, undefined);
        case ExerciseType.TEXT:
            return new TextExercise(course, undefined);
        case ExerciseType.MODELING:
            return new ModelingExercise(dto.diagramType!, course, undefined);
        case ExerciseType.FILE_UPLOAD:
            return new FileUploadExercise(course, undefined);
    }
}

function fromExerciseDetailsCourseDTO(dto: ExerciseDetailsCourseDTO): Course {
    const course = new Course();
    course.id = dto.id;
    course.title = dto.title;
    course.shortName = dto.shortName;
    course.studentGroupName = dto.studentGroupName;
    course.teachingAssistantGroupName = dto.teachingAssistantGroupName;
    course.editorGroupName = dto.editorGroupName;
    course.instructorGroupName = dto.instructorGroupName;
    course.accuracyOfScores = dto.accuracyOfScores;
    course.complaintsEnabled = dto.complaintsEnabled;
    course.requestMoreFeedbackEnabled = dto.requestMoreFeedbackEnabled;
    course.courseInformationSharingConfiguration = dto.courseInformationSharingConfiguration;
    return course;
}

function fromExerciseDetailsParticipationDTO(dto: ExerciseDetailsParticipationDTO, exercise: Exercise): StudentParticipation {
    const participation = dto.type === ParticipationType.PROGRAMMING ? new ProgrammingExerciseStudentParticipation() : new StudentParticipation();
    participation.id = dto.id;
    participation.testRun = dto.testRun;
    participation.initializationState = dto.initializationState;
    participation.initializationDate = convertDateStringFromServer(dto.initializationDate);
    participation.individualDueDate = convertDateStringFromServer(dto.individualDueDate);
    participation.presentationScore = dto.presentationScore;
    participation.student = dto.student ? Object.assign(new User(), dto.student) : undefined;
    participation.team = dto.team ? fromExerciseDetailsTeamDTO(dto.team) : undefined;
    participation.exercise = exercise;
    participation.submissions = dto.submissions?.map(fromSubmissionResponseDTO);
    participation.submissions?.forEach((submission) => (submission.participation = participation));
    return participation;
}

function fromExerciseDetailsTeamDTO(dto: ExerciseDetailsTeamDTO): Team {
    const team = new Team();
    team.id = dto.id;
    team.name = dto.name;
    team.shortName = dto.shortName;
    team.students = dto.students?.map((student) => Object.assign(new User(), student));
    return team;
}

function fromExerciseDetailsSubmissionPolicyDTO(dto: ExerciseDetailsSubmissionPolicyDTO): SubmissionPolicy {
    const policy = dto.type === SubmissionPolicyType.LOCK_REPOSITORY ? new LockRepositoryPolicy() : new SubmissionPenaltyPolicy();
    policy.id = dto.id;
    policy.type = dto.type;
    policy.submissionLimit = dto.submissionLimit;
    policy.active = dto.active;
    policy.exceedingPenalty = dto.exceedingPenalty;
    return policy;
}

function fromExerciseDetailsQuizBatchDTO(dto: ExerciseDetailsQuizBatchDTO): QuizBatch {
    const quizBatch = new QuizBatch();
    quizBatch.id = dto.id;
    quizBatch.startTime = convertDateStringFromServer(dto.startTime);
    quizBatch.started = dto.started;
    quizBatch.ended = dto.ended;
    return quizBatch;
}

function fromSerializedCategory(category: string): ExerciseCategory | undefined {
    try {
        const parsedCategory = parseJson<SerializedExerciseCategory>(category);
        return new ExerciseCategory(parsedCategory.category, parsedCategory.color);
    } catch {
        return undefined;
    }
}
