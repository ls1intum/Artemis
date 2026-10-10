import type { DomainObject } from './domain-object';
import type { QuizSubmissionBeforeEvaluation } from './quiz-submission-before-evaluation';
import type { QuizExerciseWithoutQuestions } from './quiz-exercise-without-questions';

export interface StudentQuizParticipation {
    quizQuestionsType: string;
    id?: number;
    type?: string;
    initializationState?: StudentQuizParticipationInitializationStateEnum;
    initializationDate?: string;
    student?: DomainObject;
    exercise?: QuizExerciseWithoutQuestions;
    submissions?: Array<QuizSubmissionBeforeEvaluation>;
}

export type StudentQuizParticipationInitializationStateEnum = 'UNINITIALIZED' | 'REPO_COPIED' | 'REPO_CONFIGURED' | 'INACTIVE' | 'BUILD_PLAN_COPIED' | 'BUILD_PLAN_CONFIGURED' | 'INITIALIZED' | 'FINISHED';

export const StudentQuizParticipationInitializationStateEnum = {
    Uninitialized: 'UNINITIALIZED' as const,
    RepoCopied: 'REPO_COPIED' as const,
    RepoConfigured: 'REPO_CONFIGURED' as const,
    Inactive: 'INACTIVE' as const,
    BuildPlanCopied: 'BUILD_PLAN_COPIED' as const,
    BuildPlanConfigured: 'BUILD_PLAN_CONFIGURED' as const,
    Initialized: 'INITIALIZED' as const,
    Finished: 'FINISHED' as const,
} as const;

export const StudentQuizParticipationInitializationStateEnumValues = ['UNINITIALIZED', 'REPO_COPIED', 'REPO_CONFIGURED', 'INACTIVE', 'BUILD_PLAN_COPIED', 'BUILD_PLAN_CONFIGURED', 'INITIALIZED', 'FINISHED'] as const;

