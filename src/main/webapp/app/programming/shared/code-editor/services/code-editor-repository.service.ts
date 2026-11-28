import { HttpErrorResponse, HttpParams } from '@angular/common/http';
import { OnDestroy, Service, inject } from '@angular/core';
import { Observable, ReplaySubject, Subject, UnaryFunction, defer, of, pipe, throwError } from 'rxjs';
import { catchError, finalize, map, switchMap, take, tap } from 'rxjs/operators';
import {
    CommitState,
    DomainChange,
    DomainType,
    FileSubmission,
    FileSubmissionError,
    FileType,
    GitConflictState,
    RepositoryError,
} from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { DomainDependentEndpointService } from 'app/programming/shared/code-editor/services/code-editor-domain-dependent-endpoint.service';
import { BuildLogService } from 'app/programming/shared/services/build-log.service';
import { downloadFile } from 'app/foundation/util/download.util';
import { PROBLEM_STATEMENT_IDENTIFIER } from 'app/programming/shared/code-editor/model/code-editor.model';

// Leave room below the Server's 20 MiB document limit for request metadata.
export const MAX_FILE_UPDATE_BATCH_SIZE_BYTES = 19 * 1024 * 1024;

export class RepositoryWriteCoordinator {
    private tail?: ReplaySubject<void>;

    run<T>(operation: () => Observable<T>): Observable<T> {
        return defer(() => {
            const previous: Observable<void> = this.tail ?? of(undefined);
            const finished = new ReplaySubject<void>(1);
            this.tail = finished;
            return previous.pipe(
                take(1),
                switchMap(operation),
                finalize(() => {
                    // A cancelled queued operation must still wait for its predecessor before releasing its successor.
                    previous.pipe(take(1)).subscribe(() => {
                        if (this.tail === finished) this.tail = undefined;
                        finished.next();
                        finished.complete();
                    });
                }),
            );
        });
    }
}

export interface ICodeEditorRepositoryFileService {
    runExclusive: <T>(operation: () => Observable<T>) => Observable<T>;
    getRestResourceUrl: () => string | undefined;
    getRepositoryContent: () => Observable<{ [fileName: string]: FileType }>;
    getFile: (fileName: string) => Observable<{ fileContent: string }>;
    getFileAsBlob: (fileName: string) => Observable<Blob>;
    createFile: (fileName: string) => Observable<void>;
    createFolder: (folderName: string) => Observable<void>;
    updateFileContent: (fileName: string, fileContent: string, restResourceUrl?: string) => Observable<unknown>;
    updateFiles: (
        fileUpdates: Array<{ fileName: string; fileContent: string }>,
        thenCommit?: boolean,
        restResourceUrl?: string,
    ) => Observable<FileSubmission | FileSubmissionError>;
    renameFile: (filePath: string, newFileName: string) => Observable<void>;
    deleteFile: (filePath: string) => Observable<void>;
}

export interface ICodeEditorRepositoryService {
    getRestResourceUrl: () => string | undefined;
    getStatus: () => Observable<{ repositoryStatus: string }>;
    commit: (restResourceUrl?: string) => Observable<void>;
    pull: (domain?: DomainChange) => Observable<void>;
    resetRepository: () => Observable<void>;
}

export class ConnectionError extends Error {
    constructor() {
        super('InternetDisconnected');
        // Set the prototype explicitly.
        Object.setPrototypeOf(this, ConnectionError.prototype);
    }

    static get message(): string {
        return 'InternetDisconnected';
    }
}

/**
 * Type guard for checking if the file submission received through the websocket is an error object.
 * @param toBeDetermined either a FileSubmission or a FileSubmissionError.
 */
export const checkIfSubmissionIsError = (toBeDetermined: FileSubmission | FileSubmissionError): toBeDetermined is FileSubmissionError => {
    const possibleError = toBeDetermined as Partial<FileSubmissionError>;
    return possibleError.error === RepositoryError.CHECKOUT_CONFLICT && typeof possibleError.participationId === 'number';
};

// TODO: The Repository & RepositoryFile services should be merged into 1 service, this would make handling errors easier.
/**
 * Check a HttpErrorResponse for specific status codes that are relevant for the code-editor.
 * Atm we only check the conflict status code (409) & inform the conflictService about it, and 'internet disconnected' status code (0).
 *
 * @param conflictService
 */
const handleErrorResponse = <T>(conflictService: CodeEditorConflictStateService): UnaryFunction<Observable<T>, Observable<T>> =>
    pipe(
        catchError((err: HttpErrorResponse) => {
            if (err.status === 409) {
                conflictService.notifyConflictState(GitConflictState.CHECKOUT_CONFLICT);
            }
            if (err.status === 0 || err.status === 504) {
                return throwError(() => new ConnectionError());
            }
            return throwError(() => err);
        }),
    );

@Service()
export class CodeEditorRepositoryService extends DomainDependentEndpointService implements ICodeEditorRepositoryService {
    private conflictService = inject(CodeEditorConflictStateService);

    // necessary to be used in providers array in component
    constructor() {
        super();
    }

    getStatus = () => {
        return this.http.get<{ repositoryStatus: string }>(this.restResourceUrl!).pipe(
            handleErrorResponse<{ repositoryStatus: string }>(this.conflictService),
            tap(({ repositoryStatus }) => {
                if (repositoryStatus !== CommitState.CONFLICT) {
                    this.conflictService.notifyConflictState(GitConflictState.OK);
                }
            }),
        );
    };

    commit = (restResourceUrl = this.restResourceUrl) => {
        return this.http.post<void>(`${restResourceUrl}/commit`, {}).pipe(handleErrorResponse(this.conflictService));
    };

    pull = () => {
        return this.http.post<void>(`${this.restResourceUrl}/pull`, {}).pipe(handleErrorResponse(this.conflictService));
    };

    /**
     * We don't check for conflict errors here on purpose!
     * This is the method that is used to resolve conflicts.
     */
    resetRepository = () => {
        return this.http.post<void>(`${this.restResourceUrl}/reset`, {});
    };
}

@Service()
export class CodeEditorBuildLogService extends DomainDependentEndpointService {
    private buildLogService = inject(BuildLogService);

    // necessary to be used in providers array in component
    constructor() {
        super();
    }

    getBuildLogs = () => {
        const [domainType, domainValue] = this.domain;
        if (domainType === DomainType.PARTICIPATION) {
            return this.buildLogService.getBuildLogs(domainValue.id!);
        }
        return of([]);
    };
}

@Service()
export class CodeEditorRepositoryFileService extends DomainDependentEndpointService implements ICodeEditorRepositoryFileService, OnDestroy {
    private conflictService = inject(CodeEditorConflictStateService);
    private repositoryService = inject(CodeEditorRepositoryService);

    private writeCoordinator = new RepositoryWriteCoordinator();

    runExclusive<T>(operation: () => Observable<T>): Observable<T> {
        return this.writeCoordinator.run(operation);
    }

    fileUpdateSubject = new Subject<FileSubmission>();

    // necessary to be used in providers array in component
    constructor() {
        super();
    }

    /**
     * Calls ngOnDestroy of super to unsubscribe from domain/participation changes.
     */
    override ngOnDestroy() {
        super.ngOnDestroy();
    }

    /**
     * downloads a file from the repository to the users device.
     * @param fileName the name of the file in the repository
     * @param downloadName the name of the file as suggested to the browser
     */
    downloadFile(fileName: string, downloadName: string) {
        this.http
            .get(`${this.restResourceUrl}/files-plagiarism-view`, { params: new HttpParams().set('file', fileName), responseType: 'blob' })
            .pipe(handleErrorResponse(this.conflictService))
            .subscribe((res) => {
                downloadFile(res, downloadName);
            });
    }

    /**
     * Calls setDomain of super and updates fileUpdateUrl. If this service is used at the time complete usage and unsubscribe.
     * @param domain - defines new domain of super.
     */
    override setDomain(domain: DomainChange) {
        super.setDomain(domain);
        if (this.fileUpdateSubject) {
            this.fileUpdateSubject.complete();
        }
    }

    /**
     * The endpoint prefix for reading a repository. Without a commit this is the server-side working copy, which the online editor shares with the student and which can hold
     * edits that were never submitted. With a commit, the files are read from exactly that commit and the working copy has no influence.
     * @param domain the domain of the repository, defaults to the current one
     * @param commitId the full hash of the commit to read
     */
    private readUrl(domain?: DomainChange, commitId?: string): string {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return commitId ? `${restResourceUrl}/commits/${commitId}` : `${restResourceUrl}`;
    }

    getRepositoryContent = (domain?: DomainChange, commitId?: string) => {
        return this.http
            .get<{ [fileName: string]: FileType }>(`${this.readUrl(domain, commitId)}/files`)
            .pipe(handleErrorResponse<{ [fileName: string]: FileType }>(this.conflictService));
    };

    /**
     * Gets the files of the repository for the plagiarism view.
     * @param domain the domain of the file
     */
    getRepositoryContentForPlagiarismView = (domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http
            .get<{ [fileName: string]: FileType }>(`${restResourceUrl}/files-plagiarism-view`)
            .pipe(handleErrorResponse<{ [fileName: string]: FileType }>(this.conflictService));
    };

    /**
     * Gets the files of the repository and checks whether they were changed during a student participation.
     */
    getFilesWithInformationAboutChange = (domain?: DomainChange, commitId?: string) => {
        return this.http
            .get<{ [fileName: string]: boolean }>(`${this.readUrl(domain, commitId)}/files-change`)
            .pipe(handleErrorResponse<{ [fileName: string]: boolean }>(this.conflictService));
    };

    getFile = (fileName: string, domain?: DomainChange, commitId?: string) => {
        return this.http.get(`${this.readUrl(domain, commitId)}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).pipe(
            map((data) => ({ fileContent: data })),
            handleErrorResponse<{ fileContent: string }>(this.conflictService),
        );
    };

    getFileAsBlob = (fileName: string, domain?: DomainChange, commitId?: string): Observable<Blob> => {
        return this.http
            .get(`${this.readUrl(domain, commitId)}/file`, { params: new HttpParams().set('file', fileName), responseType: 'blob' })
            .pipe(handleErrorResponse<Blob>(this.conflictService));
    };

    /**
     * Gets the file content for the plagiarism view.
     * @param fileName the name of the file
     * @param domain the domain of the file
     */
    getFileForPlagiarismView = (fileName: string, domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http.get(`${restResourceUrl}/file-plagiarism-view`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).pipe(
            map((data) => ({ fileContent: data })),
            handleErrorResponse<{ fileContent: string }>(this.conflictService),
        );
    };

    getFilesWithContent = (domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http.get<{ [fileName: string]: string }>(`${restResourceUrl}/files-content`).pipe(handleErrorResponse<{ [fileName: string]: string }>(this.conflictService));
    };

    createFile = (fileName: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/file`, '', { params: new HttpParams().set('file', fileName) }).pipe(handleErrorResponse(this.conflictService));
    };

    createFolder = (folderName: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/folder`, '', { params: new HttpParams().set('folder', folderName) }).pipe(handleErrorResponse(this.conflictService));
    };

    updateFileContent = (fileName: string, fileContent: string, restResourceUrl = this.restResourceUrl) => {
        return this.http
            .put(`${restResourceUrl}/file`, fileContent, {
                params: new HttpParams().set('file', fileName),
            })
            .pipe(handleErrorResponse(this.conflictService));
    };

    /** Call to server to update files.
     * Checks all returned files submissions for submission errors, see {@link checkIfSubmissionIsError}
     * Currently we only handle {@link GitConflictState#CHECKOUT_CONFLICT}
     *
     * @param fileUpdates the Array of updated files
     * @param thenCommit indicates the server to also commit the saved changes
     */
    updateFiles(fileUpdates: Array<{ fileName: string; fileContent: string }>, thenCommit = false, restResourceUrl = this.restResourceUrl) {
        if (this.fileUpdateSubject) {
            this.fileUpdateSubject.complete();
        }
        this.fileUpdateSubject = new Subject<FileSubmission>();

        // Filter out any PROBLEM_STATEMENT entries from the payload
        const filteredUpdates = fileUpdates.filter((update) => {
            // Only allow regular file operations - exclude PROBLEM_STATEMENT pseudo-files
            return !update.fileName.includes(PROBLEM_STATEMENT_IDENTIFIER);
        });

        let update: Observable<FileSubmission>;
        if (filteredUpdates.length === 1 && new TextEncoder().encode(JSON.stringify(filteredUpdates)).length >= MAX_FILE_UPDATE_BATCH_SIZE_BYTES) {
            const [{ fileName, fileContent }] = filteredUpdates;
            update = this.updateFileContent(fileName, fileContent, restResourceUrl).pipe(map(() => ({ [fileName]: undefined })));
        } else {
            update = this.http.put<FileSubmission>(`${restResourceUrl}/files`, filteredUpdates, {
                params: { commit: 'no' },
            });
        }
        return update.pipe(
            switchMap((submission) =>
                thenCommit && !Object.values(submission).some((error) => !!error) ? this.repositoryService.commit(restResourceUrl).pipe(map(() => submission)) : of(submission),
            ),
            handleErrorResponse(this.conflictService),
            tap((fileSubmission: FileSubmission | FileSubmissionError) => {
                if (checkIfSubmissionIsError(fileSubmission)) {
                    this.fileUpdateSubject.error(fileSubmission);
                    if (fileSubmission.error === RepositoryError.CHECKOUT_CONFLICT) {
                        this.conflictService.notifyConflictState(GitConflictState.CHECKOUT_CONFLICT);
                    }
                    return;
                }
                this.fileUpdateSubject.next(fileSubmission);
            }),
        );
    }

    renameFile = (currentFilePath: string, newFilename: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/rename-file`, { currentFilePath, newFilename }).pipe(handleErrorResponse(this.conflictService));
    };

    deleteFile = (fileName: string) => {
        return this.http.delete<void>(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName) }).pipe(handleErrorResponse(this.conflictService));
    };
}
