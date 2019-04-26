import { concat, compose, differenceWith } from 'lodash/fp';
import { differenceWith as _differenceWith } from 'lodash';
import { AnnotationArray } from 'app/entities/ace-editor/annotation.model';
import { TextChange } from './text-change.model';

type sessionObj = [string, { code: string; unsavedChanges: boolean; errors: AnnotationArray; cursor: { column: number; row: number } }];

/**
 * Wrapper class for managing editor file sessions.
 * This includes the files content (code), possible errors and a dirty flag (unsavedChanges).
 */
export class EditorFileSession {
    private fileSession: Array<sessionObj> = [];

    constructor(fileSession: Array<sessionObj> = []) {
        this.fileSession = fileSession;
    }

    public addFiles(...files: Array<{ fileName: string; code?: string; errors?: AnnotationArray; cursor?: { column: number; row: number } }>) {
        const newSessionObjs = files.map(
            ({ fileName, code, errors, cursor }): sessionObj => [
                fileName,
                { code: code || undefined, errors: errors || new AnnotationArray(), unsavedChanges: false, cursor: cursor || { column: 0, row: 0 } },
            ],
        );
        const fileSession = [...this.fileSession, ...newSessionObjs];
        return new EditorFileSession(fileSession);
    }

    public addNewFiles(...files: string[]) {
        const newSessions = files.map((file): sessionObj => [file, { code: undefined, errors: new AnnotationArray(), unsavedChanges: false, cursor: { column: 0, row: 0 } }]);
        const fileSession = [...this.fileSession, ...newSessions];
        return new EditorFileSession(fileSession);
    }

    public renameFile(oldFileName: string, newFileName: string) {
        const fileSession = this.fileSession.find(([fileName]) => fileName === oldFileName);
        if (this.fileSession) {
            const restSession = this.fileSession.filter(([fileName]) => fileName !== oldFileName);
            const newFileSession: Array<sessionObj> = [...restSession, [newFileName, fileSession[1]]];
            return new EditorFileSession(newFileSession);
        }
        return this;
    }

    /**
     * Util method for adding new files and removing old ones at the same time.
     * @param filesToAdd files for which an initialized entry should be created.
     * @param filesToRemove files that should be removed from the file session.
     */
    public update(filesToAdd: string[], filesToRemove: string[]) {
        const newEntries = filesToAdd.map(
            (fileName): sessionObj => [fileName, { errors: new AnnotationArray(), code: undefined, unsavedChanges: false, cursor: { column: 0, row: 0 } }],
        );
        const fileSession = compose(
            concat(newEntries),
            differenceWith(([a], b) => a === b, this.fileSession),
        )(filesToRemove);
        return new EditorFileSession(fileSession);
    }

    public updateErrorPositions(fileName: string, change: TextChange) {
        const fileSession = this.fileSession.map(([f, session]): sessionObj => [f, f === fileName ? { ...session, errors: session.errors.update(change) } : session]);
        return new EditorFileSession(fileSession);
    }

    public setCode(fileName: string, code: string) {
        const fileSession = this.fileSession.map(([f, session]): sessionObj => (f === fileName ? [f, { ...session, code }] : [f, session]));
        return new EditorFileSession(fileSession);
    }

    public getCode(fileName: string) {
        const session = this.fileSession.find(([f]) => f === fileName);
        return session ? session[1].code : undefined;
    }

    public getErrors(fileName: string) {
        const session = this.fileSession.find(([f]) => f === fileName);
        return session ? session[1].errors : undefined;
    }

    public setErrors(...buildLogErrors: Array<[string, AnnotationArray]>) {
        const fileSession = this.fileSession.map(
            ([fileName, session]): sessionObj => {
                const buildLog = buildLogErrors.find(([f]) => f === fileName);
                return [fileName, { ...session, errors: buildLog ? buildLog[1] : new AnnotationArray() }];
            },
        );
        return new EditorFileSession(fileSession);
    }

    public removeFiles(...fileNames: string[]) {
        const fileSession = _differenceWith(this.fileSession, fileNames, ([fileName], b) => fileName === b);
        return new EditorFileSession(fileSession);
    }

    public setUnsaved(...fileNames: string[]) {
        const fileSession = this.fileSession.map(
            ([fileName, session]): sessionObj => (fileNames.includes(fileName) ? [fileName, { ...session, unsavedChanges: true }] : [fileName, session]),
        );
        return new EditorFileSession(fileSession);
    }

    public setSaved(...fileNames: string[]) {
        const fileSession = this.fileSession.map(
            ([fileName, session]): sessionObj => (fileNames.includes(fileName) ? [fileName, { ...session, unsavedChanges: false }] : [fileName, session]),
        );
        return new EditorFileSession(fileSession);
    }

    public getCursor(fileName: string) {
        const session = this.fileSession.find(([f]) => f === fileName);
        return session ? session[1].cursor : undefined;
    }

    public setCursor(fileName: string, cursor: { column: number; row: number }) {
        const fileSession = this.fileSession.map(([f, session]): sessionObj => (f === fileName ? [f, { ...session, cursor }] : [f, session]));
        return new EditorFileSession(fileSession);
    }

    public getUnsavedFileNames() {
        return this.fileSession.filter(([, { unsavedChanges }]) => unsavedChanges).map(([fileName]) => fileName);
    }

    public getUnsavedFiles() {
        return this.fileSession.filter(([, { unsavedChanges }]) => unsavedChanges).map(([fileName, { code }]) => ({ fileName, fileContent: code }));
    }

    /**
     * Serialize the file session with all relevant attributes for persisting the file session.
     */
    public serialize() {
        return this.fileSession.reduce(
            (acc, [file, { errors }]) => ({
                ...acc,
                [file]: errors,
            }),
            {},
        );
    }

    public getLength() {
        return this.fileSession.length;
    }
}
