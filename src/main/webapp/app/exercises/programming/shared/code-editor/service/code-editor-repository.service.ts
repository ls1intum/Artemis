import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { of, pipe, Subject, throwError, UnaryFunction } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { Observable } from 'rxjs/Observable';
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
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { DomainDependentEndpointService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain-dependent-endpoint.service';
import { ProgrammingExerciseRepositoryFile } from 'app/entities/participation/ProgrammingExerciseRepositoryFile.model';

export interface ICodeEditorRepositoryFileService {
    getRepositoryContent: () => Observable<{ [fileName: string]: FileType }>;
    getFile: (fileName: string) => Observable<{ fileContent: string }>;
    createFile: (fileName: string) => Observable<void | null>;
    createFolder: (folderName: string) => Observable<void | null>;
    updateFileContent: (fileName: string, fileContent: string) => Observable<Object | null>;
    updateFiles: (fileUpdates: Array<{ fileName: string; fileContent: string }>) => Observable<{ [fileName: string]: string | null }>;
    renameFile: (filePath: string, newFileName: string) => Observable<void | null>;
    deleteFile: (filePath: string) => Observable<void | null>;
}

export interface ICodeEditorRepositoryService {
    getStatus: () => Observable<{ repositoryStatus: string }>;
    commit: () => Observable<void>;
    pull: () => Observable<void>;
    resetRepository: () => Observable<void>;
}

/**
 * Type guard for checking if the file submission received through the websocket is an error object.
 * @param toBeDetermined either a FileSubmission or a FileSubmissionError.
 */
const checkIfSubmissionIsError = (toBeDetermined: FileSubmission | FileSubmissionError): toBeDetermined is FileSubmissionError => {
    return !!(toBeDetermined as FileSubmissionError).error;
};

export const savedLocallyError: Error = new Error('Your changes could only be stored locally because you are disconnected.');

// TODO: The Repository & RepositoryFile services should be merged into 1 service, this would make handling errors easier.
/**
 * Check a HttpErrorResponse for specific status codes that are relevant for the code-editor.
 * Atm we only check the conflict status code (409) and inform the conflictService about it.
 *
 * @param conflictService
 */
const handleErrorResponse = <T>(conflictService: CodeEditorConflictStateService): UnaryFunction<Observable<T>, Observable<T>> =>
    pipe(
        catchError((err: HttpErrorResponse) => {
            if (err.status === 409) {
                conflictService.notifyConflictState(GitConflictState.CHECKOUT_CONFLICT);
            }
            // If we receive a timeout error, return ok and sync later
            if (err.status === 504) {
                conflictService.notifyConflictState(GitConflictState.OK);
            }
            return throwError(err);
        }),
    );

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryService extends DomainDependentEndpointService implements ICodeEditorRepositoryService {
    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService, private conflictService: CodeEditorConflictStateService) {
        super(http, jhiWebsocketService, domainService);
    }

    getStatus = () => {
        return this.fallbackWhenOfflineOrUnavailable(
            () =>
                this.http.get<any>(this.restResourceUrl!).pipe(
                    handleErrorResponse<{ repositoryStatus: string }>(this.conflictService),
                    tap(({ repositoryStatus }) => {
                        if (repositoryStatus !== CommitState.CONFLICT) {
                            this.conflictService.notifyConflictState(GitConflictState.OK);
                        }
                    }),
                ),
            () => of({ repositoryStatus: CommitState.UNCOMMITTED_CHANGES }), // TODO track change status when offline
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
    constructor(private buildLogService: BuildLogService, http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
    }

    getBuildLogs = () => {
        const [domainType, domainValue] = this.domain;
        if (domainType === DomainType.PARTICIPATION) {
            return this.buildLogService.getBuildLogs(domainValue.id);
        }
        return of([]);
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryFileService extends DomainDependentEndpointService implements ICodeEditorRepositoryFileService, OnDestroy {
    private fileUpdateSubject = new Subject<FileSubmission>();
    private fileUpdateUrl: string;

    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService, private conflictService: CodeEditorConflictStateService) {
        super(http, jhiWebsocketService, domainService);
    }

    /**
     * Calls ngOnDestroy of super and unsubscribes from current service.
     */
    ngOnDestroy() {
        super.ngOnDestroy();
        this.jhiWebsocketService.unsubscribe(this.fileUpdateUrl);
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
        if (this.fileUpdateUrl) {
            this.jhiWebsocketService.unsubscribe(this.fileUpdateUrl);
        }
        this.fileUpdateUrl = `${this.websocketResourceUrlReceive}/files`;
    }

    onGotOnline = () => {
        const unsynchedFiles = this.participation?.unsynchedFiles;
        if (unsynchedFiles && unsynchedFiles?.length > 0) {
            return this.updateFiles(unsynchedFiles)
                .first()
                .subscribe(() => this.participation.unsynchedFiles.splice(0, this.participation.unsynchedFiles.length));
        }
    };

    getRepositoryContent() {
        return this.fallbackWhenOfflineOrUnavailable(
            () =>
                this.http.get<{ [fileName: string]: FileType }>(`${this.restResourceUrl}/files`).pipe(handleErrorResponse<{ [fileName: string]: FileType }>(this.conflictService)),
            () => this.participationObservable().map(({ repositoryFiles }) => this.getFilenameAndType(repositoryFiles)),
        );
    }

    getFile(fileName: string) {
        const file = this.participation?.unsynchedFiles?.find((f) => f.fileName === fileName);
        if (file) {
            return of(file);
        }

        return this.fallbackWhenOfflineOrUnavailable(
            () =>
                this.http.get(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).pipe(
                    map((data) => ({ fileContent: data })),
                    handleErrorResponse<{ fileContent: string }>(this.conflictService),
                ),
            () => this.participationObservable().map(({ repositoryFiles }) => repositoryFiles.find((f) => f.filename === fileName) || { fileContent: '' }),
        );
    }

    createFile(fileName: string) {
        if (this.participation) {
            this.participation.repositoryFiles?.push(Object.assign(new ProgrammingExerciseRepositoryFile(), { filename: fileName, fileType: FileType.FILE, fileContent: '' }));
        }

        return this.fallbackWhenOfflineOrUnavailable(
            () =>
                this.http
                    .post<void>(`${this.restResourceUrl}/file`, '', { params: new HttpParams().set('file', fileName) })
                    .pipe(handleErrorResponse(this.conflictService)),
            () => {
                if (this.participation?.unsynchedFiles) {
                    this.participation.unsynchedFiles.push({ fileName, fileContent: '' });
                } else if (this.participation) {
                    this.participation.unsynchedFiles = [{ fileName, fileContent: '' }];
                }
                return of(null);
            },
            true,
        );
    }

    createFolder(folderName: string) {
        if (this.participation) {
            this.participation.repositoryFiles?.push(Object.assign(new ProgrammingExerciseRepositoryFile(), { filename: folderName, fileType: FileType.FOLDER, fileContent: '' }));
        }

        return this.fallbackWhenOfflineOrUnavailable(
            () =>
                this.http
                    .post<void>(`${this.restResourceUrl}/folder`, '', { params: new HttpParams().set('folder', folderName) })
                    .pipe(handleErrorResponse(this.conflictService)),
            () => of(null),
            true,
        );
    }

    // This method is never called
    updateFileContent(fileName: string, fileContent: string) {
        const file = this.participation?.repositoryFiles?.find((f) => f.filename === fileName);
        if (file) {
            file.fileContent = fileContent;
        }

        return this.fallbackWhenOfflineOrUnavailable(
            () => this.http.put(`${this.restResourceUrl}/file`, fileContent, { params: new HttpParams().set('file', fileName) }).pipe(handleErrorResponse(this.conflictService)),
            () => {
                const syncFile = this.participation?.unsynchedFiles?.find((f) => f.fileName === fileName);
                if (syncFile) {
                    syncFile.fileContent = fileContent;
                }
                return of(null);
            },
            true,
        );
    }

    updateFiles(fileUpdates: Array<{ fileName: string; fileContent: string }>) {
        if (this.fileUpdateSubject) {
            this.fileUpdateSubject.complete();
        }
        // First store the newest changes in the participation
        if (this.participation && this.participation.repositoryFiles) {
            fileUpdates.forEach((update) => {
                const fileToUpdate = this.participation.repositoryFiles.find((file) => file.filename === update.fileName);
                if (fileToUpdate) {
                    fileToUpdate.fileContent = update.fileContent;
                }
            });
        }

        if (!this.isOnline) {
            if (this.participation) {
                if (!this.participation.unsynchedFiles) {
                    this.participation.unsynchedFiles = [];
                }
                for (const file of fileUpdates) {
                    const index = this.participation.unsynchedFiles.findIndex((f) => f.fileName === file.fileName);
                    if (index >= 0) {
                        this.participation.unsynchedFiles[index].fileContent = file.fileContent;
                    } else {
                        this.participation.unsynchedFiles.push(file);
                    }
                }
            }
            return throwError(savedLocallyError);
        }

        if (this.fileUpdateUrl) {
            this.jhiWebsocketService.unsubscribe(this.fileUpdateUrl);
        }

        this.fileUpdateSubject = new Subject<FileSubmission>();

        this.jhiWebsocketService.subscribe(this.fileUpdateUrl);
        this.jhiWebsocketService
            .receive(this.fileUpdateUrl)
            .pipe(
                tap((fileSubmission: FileSubmission | FileSubmissionError) => {
                    if (checkIfSubmissionIsError(fileSubmission)) {
                        // The subject gets informed about all errors.
                        this.fileUpdateSubject.error(fileSubmission);
                        // Checkout conflict handling.
                        if (checkIfSubmissionIsError(fileSubmission) && fileSubmission.error === RepositoryError.CHECKOUT_CONFLICT) {
                            this.conflictService.notifyConflictState(GitConflictState.CHECKOUT_CONFLICT);
                        }
                        return;
                    }
                    this.fileUpdateSubject.next(fileSubmission);
                }),
                catchError(() => of()),
            )
            .subscribe();
        // TODO: This is a hotfix for the subscribe/unsubscribe mechanism of the websocket service. Without this, the SEND might be sent before the SUBSCRIBE.
        setTimeout(() => {
            this.jhiWebsocketService.send(`${this.websocketResourceUrlSend}/files`, fileUpdates);
        });
        return this.fileUpdateSubject.asObservable();
    }

    renameFile(currentFilePath: string, newFilename: string) {
        const file = this.participation?.repositoryFiles?.find((f) => f.filename === currentFilePath);
        if (file) {
            file.filename = currentFilePath.substring(0, currentFilePath.lastIndexOf('/') + 1) + newFilename;
        }

        return this.fallbackWhenOfflineOrUnavailable(
            () =>
                this.http
                    .post<void>(`${this.restResourceUrl}/rename-file`, { currentFilePath, newFilename })
                    .pipe(handleErrorResponse(this.conflictService)),
            () => {
                const syncFile = this.participation?.unsynchedFiles?.find((f) => f.fileName === currentFilePath);
                if (syncFile) {
                    syncFile.fileName = newFilename;
                }
                return of(null);
            },
            true,
        );
    }

    deleteFile(fileName: string) {
        const fileIndex = this.participation?.repositoryFiles?.findIndex((f) => f.filename === fileName);
        if (fileIndex != null && fileIndex >= 0) {
            this.participation.repositoryFiles.splice(fileIndex, 1);
        }

        return this.fallbackWhenOfflineOrUnavailable(
            () =>
                this.http
                    .delete<void>(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName) })
                    .pipe(handleErrorResponse(this.conflictService)),
            () => {
                const index = this.participation?.unsynchedFiles?.findIndex((f) => f.fileName === fileName);
                if (index != null && index >= 0) {
                    this.participation.unsynchedFiles.splice(index, 1);
                }
                return of(null);
            },
            true,
        );
    }

    getFilenameAndType(files: ProgrammingExerciseRepositoryFile[]) {
        const fileDict: { [filename: string]: FileType } = {};
        files.forEach((file) => {
            fileDict[file.filename] = file.fileType;
        });
        return fileDict;
    }
}
