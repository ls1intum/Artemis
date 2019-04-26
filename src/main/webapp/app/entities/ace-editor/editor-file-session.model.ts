import { concat, compose, differenceWith } from 'lodash/fp';
import { differenceWith as _differenceWith } from 'lodash';
import { AnnotationArray } from 'app/entities/ace-editor/annotation.model';
import { TextChange } from './text-change.model';

type sessionObj = [string, { code: string; unsavedChanges: boolean; errors: AnnotationArray; cursor: { column: number; row: number } }];
export type FileSessions = Array<sessionObj>;

/**
 * Wrapper class for managing editor file sessions.
 * This includes the files content (code), possible errors and a dirty flag (unsavedChanges).
 */
export abstract class EditorFileSession {
    public static create() {
        return [] as FileSessions;
    }

    public static getFileNames(sessions: FileSessions) {
        return sessions.map(([fileName]) => fileName);
    }

    public static addFiles(sessions: FileSessions, ...files: Array<{ fileName: string; code?: string; errors?: AnnotationArray; cursor?: { column: number; row: number } }>) {
        const newSessionObjs = files.map(
            ({ fileName, code, errors, cursor }): sessionObj => [
                fileName,
                { code: code || undefined, errors: errors || new AnnotationArray(), unsavedChanges: false, cursor: cursor || { column: 0, row: 0 } },
            ],
        );
        return [...sessions, ...newSessionObjs];
    }

    public static addNewFiles(sessions: FileSessions, ...files: string[]) {
        const newSessions = files.map((file): sessionObj => [file, { code: undefined, errors: new AnnotationArray(), unsavedChanges: false, cursor: { column: 0, row: 0 } }]);
        return [...sessions, ...newSessions];
    }

    public static renameFile(sessions: FileSessions, oldFileName: string, newFileName: string): FileSessions {
        const renamedFileSession = sessions.find(([fileName]) => fileName === oldFileName);
        if (renamedFileSession) {
            const restSession = sessions.filter(([fileName]) => fileName !== oldFileName);
            return [...restSession, [newFileName, renamedFileSession[1]]];
        }
        return sessions;
    }

    /**
     * Util method for adding new files and removing old ones at the same time.
     * @param filesToAdd files for which an initialized entry should be created.
     * @param filesToRemove files that should be removed from the file session.
     */
    public static update(sessions: FileSessions, filesToAdd: string[], filesToRemove: string[]) {
        const newEntries = filesToAdd.map(
            (fileName): sessionObj => [fileName, { errors: new AnnotationArray(), code: undefined, unsavedChanges: false, cursor: { column: 0, row: 0 } }],
        );
        const fileSession = compose(
            concat(newEntries),
            differenceWith(([a], b) => a === b, sessions),
        )(filesToRemove);
        return fileSession;
    }

    public static updateErrorPositions(sessions: FileSessions, fileName: string, change: TextChange) {
        return sessions.map(([f, session]): sessionObj => [f, f === fileName ? { ...session, errors: session.errors.update(change) } : session]);
    }

    public static setCode(sessions: FileSessions, fileName: string, code: string) {
        return sessions.map(([f, session]): sessionObj => (f === fileName ? [f, { ...session, code }] : [f, session]));
    }

    public static getCode(sessions: FileSessions, fileName: string) {
        const fileSession = sessions.find(([f]) => f === fileName);
        return fileSession ? fileSession[1].code : undefined;
    }

    public static getErrors(sessions: FileSessions, fileName: string) {
        const session = sessions.find(([f]) => f === fileName);
        return session ? session[1].errors : undefined;
    }

    public static setErrors(sessions: FileSessions, fileName: string, errors: AnnotationArray) {
        return sessions.map(([f, session]): sessionObj => (f === fileName ? [f, { ...session, errors }] : [f, session]));
    }

    public static setErrorsFromBuildLogs(sessions: FileSessions, ...buildLogErrors: Array<[string, AnnotationArray]>) {
        return sessions.map(
            ([fileName, session]): sessionObj => {
                const buildLog = buildLogErrors.find(([f]) => f === fileName);
                return [fileName, { ...session, errors: buildLog ? buildLog[1] : new AnnotationArray() }];
            },
        );
    }

    public static removeFiles(sessions: FileSessions, ...fileNames: string[]) {
        return _differenceWith(sessions, fileNames, ([fileName], b) => fileName === b);
    }

    public static setUnsaved(sessions: FileSessions, ...fileNames: string[]) {
        return sessions.map(([fileName, session]): sessionObj => (fileNames.includes(fileName) ? [fileName, { ...session, unsavedChanges: true }] : [fileName, session]));
    }

    public static setSaved(sessions: FileSessions, ...fileNames: string[]) {
        return sessions.map(([fileName, session]): sessionObj => (fileNames.includes(fileName) ? [fileName, { ...session, unsavedChanges: false }] : [fileName, session]));
    }

    public static getCursor(sessions: FileSessions, fileName: string) {
        const session = sessions.find(([f]) => f === fileName);
        return session ? session[1].cursor : undefined;
    }

    public static setCursor(sessions: FileSessions, fileName: string, cursor: { column: number; row: number }) {
        return sessions.map(([f, session]): sessionObj => (f === fileName ? [f, { ...session, cursor }] : [f, session]));
    }

    public static getUnsavedFileNames(sessions: FileSessions) {
        return sessions.filter(([, { unsavedChanges }]) => unsavedChanges).map(([fileName]) => fileName);
    }

    public static getUnsavedFiles(sessions: FileSessions) {
        return sessions.filter(([, { unsavedChanges }]) => unsavedChanges).map(([fileName, { code }]) => ({ fileName, fileContent: code }));
    }

    public static hasUnsavedChanges(sessions: FileSessions, fileName: string) {
        const session = sessions.find(([f]) => f === fileName);
        return session ? session[1].unsavedChanges : undefined;
    }

    /**
     * Serialize the file session with all relevant attributes for persisting the file session.
     */
    public static serialize(sessions: FileSessions) {
        return sessions.reduce(
            (acc, [file, { errors }]) => ({
                ...acc,
                [file]: errors,
            }),
            {},
        );
    }

    public static getLength(sessions: FileSessions) {
        return sessions.length;
    }
}
