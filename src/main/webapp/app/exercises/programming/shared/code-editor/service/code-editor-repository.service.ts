import { HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subject, UnaryFunction, of, pipe, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import {
    CommitState,
    DomainChange,
    DomainType,
    FileSubmission,
    FileSubmissionError,
    FileType,
    GitConflictState,
    RepositoryError,
} from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { DomainDependentEndpointService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain-dependent-endpoint.service';
import { downloadFile } from 'app/shared/util/download.util';

export interface ICodeEditorRepositoryFileService {
    getRepositoryContent: () => Observable<{ [fileName: string]: FileType }>;
    getFile: (fileName: string) => Observable<{ fileContent: string }>;
    createFile: (fileName: string) => Observable<void>;
    createFolder: (folderName: string) => Observable<void>;
    updateFileContent: (fileName: string, fileContent: string) => Observable<any>;
    updateFiles: (fileUpdates: Array<{ fileName: string; fileContent: string }>) => Observable<FileSubmission | FileSubmissionError>;
    renameFile: (filePath: string, newFileName: string) => Observable<void>;
    deleteFile: (filePath: string) => Observable<void>;
}

export interface ICodeEditorRepositoryService {
    getStatus: () => Observable<{ repositoryStatus: string }>;
    commit: () => Observable<void>;
    pull: () => Observable<void>;
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
const checkIfSubmissionIsError = (toBeDetermined: FileSubmission | FileSubmissionError): toBeDetermined is FileSubmissionError => {
    return !!(toBeDetermined as FileSubmissionError).error;
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

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryService extends DomainDependentEndpointService implements ICodeEditorRepositoryService {
    private conflictService = inject(CodeEditorConflictStateService);

    getStatus = () => {
        return this.http.get<any>(this.restResourceUrl!).pipe(
            handleErrorResponse<{ repositoryStatus: string }>(this.conflictService),
            tap(({ repositoryStatus }) => {
                if (repositoryStatus !== CommitState.CONFLICT) {
                    this.conflictService.notifyConflictState(GitConflictState.OK);
                }
            }),
        );
    };

    commit = () => {
        return this.http.post<void>(`${this.restResourceUrl}/commit`, {}).pipe(handleErrorResponse(this.conflictService));
    };

    pull = () => {
        return this.http.get<void>(`${this.restResourceUrl}/pull`, {}).pipe(handleErrorResponse(this.conflictService));
    };

    /**
     * We don't check for conflict errors here on purpose!
     * This is the method that is used to resolve conflicts.
     */
    resetRepository = () => {
        return this.http.post<void>(`${this.restResourceUrl}/reset`, {});
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorBuildLogService extends DomainDependentEndpointService {
    private buildLogService = inject(BuildLogService);

    getBuildLogs = () => {
        const [domainType, domainValue] = this.domain;
        if (domainType === DomainType.PARTICIPATION) {
            return this.buildLogService.getBuildLogs(domainValue.id!);
        }
        return of([]);
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryFileService extends DomainDependentEndpointService implements ICodeEditorRepositoryFileService, OnDestroy {
    private conflictService = inject(CodeEditorConflictStateService);
    private fileUpdateSubject = new Subject<FileSubmission>();

    /**
     * Calls ngOnDestroy of super to unsubscribe from domain/participation changes.
     */
    ngOnDestroy() {
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
    setDomain(domain: DomainChange) {
        super.setDomain(domain);
        if (this.fileUpdateSubject) {
            this.fileUpdateSubject.complete();
        }
    }

    getRepositoryContent = (domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http.get<{ [fileName: string]: FileType }>(`${restResourceUrl}/files`).pipe(handleErrorResponse<{ [fileName: string]: FileType }>(this.conflictService));
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
    getFilesWithInformationAboutChange = (domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http.get<{ [fileName: string]: boolean }>(`${restResourceUrl}/files-change`).pipe(handleErrorResponse<{ [fileName: string]: boolean }>(this.conflictService));
    };

    getFile = (fileName: string, domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http.get(`${restResourceUrl}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).pipe(
            map((data) => ({ fileContent: data })),
            handleErrorResponse<{ fileContent: string }>(this.conflictService),
        );
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

    getFileHeaders = (fileName: string, domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http
            .head<Blob>(`${restResourceUrl}/file-plagiarism-view`, { observe: 'response', params: new HttpParams().set('file', fileName) })
            .pipe(handleErrorResponse(this.conflictService));
    };

    getFilesWithContent = (domain?: DomainChange) => {
        const restResourceUrl = domain ? this.calculateRestResourceURL(domain) : this.restResourceUrl;
        return this.http.get(`${restResourceUrl}/files-content`).pipe(handleErrorResponse<{ [fileName: string]: string }>(this.conflictService));
    };

    createFile = (fileName: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/file`, '', { params: new HttpParams().set('file', fileName) }).pipe(handleErrorResponse(this.conflictService));
    };

    createFolder = (folderName: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/folder`, '', { params: new HttpParams().set('folder', folderName) }).pipe(handleErrorResponse(this.conflictService));
    };

    updateFileContent = (fileName: string, fileContent: string) => {
        return this.http
            .put(`${this.restResourceUrl}/file`, fileContent, {
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
    updateFiles(fileUpdates: Array<{ fileName: string; fileContent: string }>, thenCommit = false) {
        if (this.fileUpdateSubject) {
            this.fileUpdateSubject.complete();
        }
        this.fileUpdateSubject = new Subject<FileSubmission>();
        return this.http
            .put<FileSubmission>(`${this.restResourceUrl}/files`, fileUpdates, {
                params: { commit: thenCommit ? 'yes' : 'no' },
            })
            .pipe(
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
