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
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExerciseRepositoryFile } from 'app/entities/participation/ProgrammingExerciseRepositoryFile.model';

export interface ICodeEditorRepositoryFileService {
    getRepositoryContent: () => Observable<{ [fileName: string]: FileType }>;
    getFileFromRepository: (fileName: string) => Observable<{ fileContent: string }>;
    createFile: (fileName: string) => Observable<void>;
    createFolder: (folderName: string) => Observable<void>;
    updateFileContent: (fileName: string, fileContent: string) => Observable<Object>;
    updateFiles: (fileUpdates: Array<{ fileName: string; fileContent: string }>) => Observable<{ [fileName: string]: string | null }>;
    renameFile: (filePath: string, newFileName: string) => Observable<void>;
    deleteFile: (filePath: string) => Observable<void>;
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

    // TODO: Mock this call for offline code editor support
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

    private participations: ProgrammingExerciseStudentParticipation[] = [];

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

    /**
     * Fetches the content of the repository from the participation.
     * Offline alternative to {@link getRepositoryContent}
     */
    getParticipationContent() {
        const files = this.getParticipation(this.domainValue.id)?.repositoryFiles;
        if (files) {
            return of(this.getFilenameAndType(files));
        }
    }

    getRepositoryContent = () => {
        // TODO: If offline retrieve cached repository content
        // TODO: If we use the server update the cached file list
        return this.http.get<{ [fileName: string]: FileType }>(`${this.restResourceUrl}/files`).pipe(handleErrorResponse<{ [fileName: string]: FileType }>(this.conflictService));
    };

    getFileFromRepository = (fileName: string) => {
        return this.http.get(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).pipe(
            map((data) => ({ fileContent: data })),
            handleErrorResponse<{ fileContent: string }>(this.conflictService),
        );
    };

    getFileFromParticipation = (fileName: string) => {
        const files = this.getParticipation(this.domainValue.id)?.repositoryFiles;
        if (files) {
            files.filter((file) => {
                return file.filename === fileName;
            });
        }
    };

    createFile = (fileName: string) => {
        return this.http
            .post<void>(`${this.restResourceUrl}/file`, '', { params: new HttpParams().set('file', fileName) })
            .pipe(handleErrorResponse(this.conflictService));
    };

    createFolder = (folderName: string) => {
        return this.http
            .post<void>(`${this.restResourceUrl}/folder`, '', { params: new HttpParams().set('folder', folderName) })
            .pipe(handleErrorResponse(this.conflictService));
    };

    updateFileContent = (fileName: string, fileContent: string) => {
        return this.http
            .put(`${this.restResourceUrl}/file`, fileContent, {
                params: new HttpParams().set('file', fileName),
            })
            .pipe(handleErrorResponse(this.conflictService));
    };

    updateFiles = (fileUpdates: Array<{ fileName: string; fileContent: string }>) => {
        if (this.fileUpdateSubject) {
            this.fileUpdateSubject.complete();
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
    };

    renameFile = (currentFilePath: string, newFilename: string) => {
        return this.http
            .post<void>(`${this.restResourceUrl}/rename-file`, { currentFilePath, newFilename })
            .pipe(handleErrorResponse(this.conflictService));
    };

    deleteFile = (fileName: string) => {
        return this.http
            .delete<void>(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName) })
            .pipe(handleErrorResponse(this.conflictService));
    };

    addParticipation(participation: ProgrammingExerciseStudentParticipation) {
        // TODO: Handle the case when a participation with the same ID or for the same exercise already exists
        this.participations.push(participation);
    }

    getParticipation(participationId: number): ProgrammingExerciseStudentParticipation | undefined {
        return this.participations.find((participation) => participation.id === participationId);
    }

    getFilenameAndType(files: ProgrammingExerciseRepositoryFile[]) {
        const fileDict: { [filename: string]: FileType } = {};
        files.forEach((file) => {
            fileDict[file.filename] = file.fileType;
        });
        return fileDict;
    }
}
