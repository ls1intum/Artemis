import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

/**
 * The kind of VCS access token shown in the user-settings token overview. Used together with the token id to revoke a token.
 */
export enum VcsAccessTokenType {
    PARTICIPATION = 'PARTICIPATION',
    REPOSITORY = 'REPOSITORY',
}

/**
 * A single VCS access token a user owns, as shown in the user-settings token overview. Carries only display metadata, never the token secret.
 */
export interface VcsAccessTokenOverview {
    id: number;
    tokenType: VcsAccessTokenType;
    // Only set for repository-scoped tokens (TEMPLATE, SOLUTION, TESTS, AUXILIARY or USER); undefined for participation tokens.
    repositoryType?: RepositoryType;
    // The course the token's exercise belongs to (disambiguates exercises with the same title across courses); id for linking, title for display.
    courseId?: number;
    courseTitle?: string;
    // For an exam exercise, the exam and exercise-group ids so the client can build the correct exercise link; undefined for a regular course exercise.
    examId?: number;
    exerciseGroupId?: number;
    // The programming exercise the token's repository belongs to; id for linking, title for display.
    exerciseId?: number;
    exerciseTitle?: string;
    // Only set for a repository-scoped USER token: the login of the student whose assignment repository the token grants access to.
    studentLogin?: string;
    // The canonical URI of the repository the token grants access to, so it is unambiguous which repository is meant.
    repositoryUri?: string;
}
