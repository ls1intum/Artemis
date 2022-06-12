import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

/**
 * Enumeration defining type of the exported file.
 */
export enum FileType {
    FILE = 'FILE',
    FOLDER = 'FOLDER',
}

export abstract class FileChange {}

export class CreateFileChange extends FileChange {
    constructor(public fileType: FileType, public fileName: string) {
        super();
    }
}

export class DeleteFileChange extends FileChange {
    constructor(public fileType: FileType, public fileName: string) {
        super();
    }
}

export class RenameFileChange extends FileChange {
    constructor(public fileType: FileType, public oldFileName: string, public newFileName: string) {
        super();
    }
}

export type FileSubmission = { [fileName: string]: string | undefined };

/**
 * Enumeration defining domain type.
 */
export enum DomainType {
    PARTICIPATION = 'PARTICIPATION',
    TEST_REPOSITORY = 'TEST_REPOSITORY',
}

/**
 * Enumeration defining whether there is a conflict while checking out.
 */
export enum RepositoryError {
    CHECKOUT_CONFLICT = 'checkoutConflict',
}

export type FileSubmissionError = { error: RepositoryError; participationId: number; fileName: string };

/**
 * Enumeration defining the state of the commit.
 */
export enum CommitState {
    UNDEFINED = 'UNDEFINED',
    COULD_NOT_BE_RETRIEVED = 'COULD_NOT_BE_RETRIEVED',
    CLEAN = 'CLEAN',
    UNCOMMITTED_CHANGES = 'UNCOMMITTED_CHANGES',
    COMMITTING = 'COMMITTING',
    CONFLICT = 'CONFLICT',
}

/**
 * Enumeration defining the state of the editor.
 */
export enum EditorState {
    CLEAN = 'CLEAN',
    UNSAVED_CHANGES = 'UNSAVED_CHANGES',
    SAVING = 'SAVING',
    REFRESHING = 'REFRESHING',
}

/**
 * Enumeration defining type of the resize operation.
 */
export enum ResizeType {
    SIDEBAR_LEFT = 'SIDEBAR_LEFT',
    SIDEBAR_RIGHT = 'SIDEBAR_RIGHT',
    MAIN_BOTTOM = 'MAIN_BOTTOM',
    BOTTOM = 'BOTTOM',
}

export type DomainParticipationChange = [DomainType.PARTICIPATION, StudentParticipation | TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation];
export type DomainTestRepositoryChange = [DomainType.TEST_REPOSITORY, ProgrammingExercise];
export type DomainChange = DomainParticipationChange | DomainTestRepositoryChange;

/**
 * Enumeration defining the state of git.
 */
export enum GitConflictState {
    CHECKOUT_CONFLICT = 'CHECKOUT_CONFLICT',
    OK = 'OK',
}
