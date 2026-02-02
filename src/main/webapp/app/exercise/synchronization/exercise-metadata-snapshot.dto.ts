import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { DifficultyLevel, ExerciseMode, IncludedInOverallScore, PlagiarismDetectionConfig } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';

export interface ExerciseSnapshotDTO {
    id: number;
    title?: string;
    shortName?: string;
    channelName?: string;
    competencyLinks?: CompetencyExerciseLinkSnapshotDTO[];
    maxPoints?: number;
    bonusPoints?: number;
    assessmentType?: AssessmentType;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
    difficulty?: DifficultyLevel;
    mode?: ExerciseMode;
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    includedInOverallScore?: IncludedInOverallScore;
    problemStatement?: string;
    gradingInstructions?: string;
    categories?: string[];
    teamAssignmentConfig?: TeamAssignmentConfigSnapshot;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;
    feedbackSuggestionModule?: string;
    gradingCriteria?: GradingCriterion[];
    plagiarismDetectionConfig?: PlagiarismDetectionConfig;
    programmingData?: ProgrammingExerciseSnapshotDTO;
    textData?: TextExerciseSnapshotDTO;
    modelingData?: ModelingExerciseSnapshotDTO;
    quizData?: QuizExerciseSnapshotDTO;
    fileUploadData?: FileUploadExerciseSnapshotDTO;
}

export interface TeamAssignmentConfigSnapshot {
    id?: number;
    minTeamSize?: number;
    maxTeamSize?: number;
}

export interface CompetencyExerciseLinkSnapshotDTO {
    competencyId?: CompetencyExerciseIdSnapshotDTO;
    weight?: number;
}

export interface CompetencyExerciseIdSnapshotDTO {
    exerciseId?: number;
    competencyId?: number;
}

export interface ProgrammingExerciseSnapshotDTO {
    testRepositoryUri?: string;
    auxiliaryRepositories?: AuxiliaryRepositorySnapshotDTO[];
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    allowOnlineIde?: boolean;
    staticCodeAnalysisEnabled?: boolean;
    maxStaticCodeAnalysisPenalty?: number;
    programmingLanguage?: ProgrammingLanguage;
    packageName?: string;
    showTestNamesToStudents?: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: string;
    projectKey?: string;
    templateParticipation?: ParticipationSnapshotDTO;
    solutionParticipation?: ParticipationSnapshotDTO;
    submissionPolicy?: SubmissionPolicySnapshotDTO;
    projectType?: ProjectType;
    releaseTestsWithExampleSolution?: boolean;
    buildConfig?: ProgrammingExerciseBuildConfigSnapshotDTO;
}

export interface AuxiliaryRepositorySnapshotDTO {
    id?: number;
    repositoryUri?: string;
}

export interface ParticipationSnapshotDTO {
    id?: number;
    repositoryUri?: string;
    buildPlanId?: string;
}

export interface SubmissionPolicySnapshotDTO {
    id?: number;
    submissionLimit?: number;
    active?: boolean;
    exceedingPenalty?: number;
    type?: string;
}

export interface ProgrammingExerciseBuildConfigSnapshotDTO {
    sequentialTestRuns?: boolean;
    branch?: string;
    buildPlanConfiguration?: string;
    buildScript?: string;
    checkoutSolutionRepository?: boolean;
    testCheckoutPath?: string;
    assignmentCheckoutPath?: string;
    solutionCheckoutPath?: string;
    timeoutSeconds?: number;
    dockerFlags?: string;
    theiaImage?: string;
    allowBranching?: boolean;
    branchRegex?: string;
}

export interface TextExerciseSnapshotDTO {
    exampleSolution?: string;
}

export interface ModelingExerciseSnapshotDTO {
    diagramType?: string;
    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;
}

export interface QuizExerciseSnapshotDTO {
    randomizeQuestionOrder?: boolean;
    allowedNumberOfAttempts?: number;
    quizMode?: QuizMode;
    duration?: number;
    quizQuestions?: QuizQuestionSnapshotDTO[];
}

export interface QuizQuestionSnapshotDTO {
    id?: number;
    title?: string;
    text?: string;
    hint?: string;
    explanation?: string;
    points?: number;
    scoringType?: ScoringType;
    randomizeOrder?: boolean;
    invalid?: boolean;
    type?: QuizQuestionType | string;
    answerOptions?: AnswerOptionSnapshotDTO[];
    singleChoice?: boolean;
    backgroundFilePath?: string;
    dropLocations?: DropLocationSnapshotDTO[];
    dragItems?: DragItemSnapshotDTO[];
    correctMappings?: DragAndDropMappingSnapshotDTO[] | ShortAnswerMappingSnapshotDTO[];
    spots?: ShortAnswerSpotSnapshotDTO[];
    solutions?: ShortAnswerSolutionSnapshotDTO[];
    similarityValue?: number;
    matchLetterCase?: boolean;
}

export interface AnswerOptionSnapshotDTO {
    id?: number;
    text?: string;
    hint?: string;
    invalid?: boolean;
    explanation?: string;
    isCorrect?: boolean;
}

export interface DropLocationSnapshotDTO {
    id?: number;
    posX?: number;
    posY?: number;
    width?: number;
    height?: number;
    invalid?: boolean;
}

export interface DragItemSnapshotDTO {
    id?: number;
    pictureFilePath?: string;
    text?: string;
    invalid?: boolean;
}

export interface DragAndDropMappingSnapshotDTO {
    id?: number;
    dragItemIndex?: number;
    dropLocationIndex?: number;
    invalid?: boolean;
    dragItem?: DragItemSnapshotDTO;
    dropLocation?: DropLocationSnapshotDTO;
}

export interface ShortAnswerSpotSnapshotDTO {
    id?: number;
    spotNr?: number;
    width?: number;
    invalid?: boolean;
}

export interface ShortAnswerSolutionSnapshotDTO {
    id?: number;
    text?: string;
    invalid?: boolean;
}

export interface ShortAnswerMappingSnapshotDTO {
    id?: number;
    shortAnswerSpotIndex?: number;
    shortAnswerSolutionIndex?: number;
    invalid?: boolean;
    solution?: ShortAnswerSolutionSnapshotDTO;
    spot?: ShortAnswerSpotSnapshotDTO;
}

export interface FileUploadExerciseSnapshotDTO {
    exampleSolution?: string;
    filePattern?: string;
}
