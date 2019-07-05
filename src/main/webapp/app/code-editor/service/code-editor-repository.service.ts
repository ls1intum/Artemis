import { HttpClient, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs/Observable';

import { BuildLogEntry } from 'app/entities/build-log';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { JhiWebsocketService } from 'app/core';
import { DomainChange, DomainDependent, DomainDependentEndpoint, DomainService } from 'app/code-editor/service';

export enum DomainType {
    PARTICIPATION = 'PARTICIPATION',
    TEST_REPOSITORY = 'TEST_REPOSITORY',
}

export enum GitConflictState {
    CHECKOUT_CONFLICT = 'CHECKOUT_CONFLICT',
    OK = 'OK',
}

export interface IConflictStateService {
    subscribeConflictState: () => Observable<GitConflictState>;
}

export interface ICodeEditorRepositoryFileService {
    getRepositoryContent: () => Observable<{ [fileName: string]: FileType }>;
    getFile: (fileName: string) => Observable<{ fileContent: string }>;
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

export interface IBuildLogService {
    getBuildLogs: () => Observable<BuildLogEntry[]>;
}

@Injectable({ providedIn: 'root' })
export class ConflictStateService extends DomainDependent implements IConflictStateService, OnDestroy {
    private conflictSubjects: Map<string, BehaviorSubject<GitConflictState>> = new Map();
    private websocketConnections: Map<string, string> = new Map();

    constructor(domainService: DomainService, private jhiWebsocketService: JhiWebsocketService) {
        super(domainService);
        this.initDomainSubscription();
    }

    ngOnDestroy(): void {
        Object.values(this.websocketConnections).forEach(channel => this.jhiWebsocketService.unsubscribe(channel));
    }

    subscribeConflictState = () => {
        const domainKey = this.getDomainKey();

        if (!this.conflictSubjects[domainKey]) {
            const repoSubject = new BehaviorSubject(GitConflictState.OK);

            const repoStateUpdateChannel = `/topic/user/repository-state/${domainKey}/conflict`;
            this.jhiWebsocketService.subscribe(repoStateUpdateChannel);
            this.jhiWebsocketService
                .receive(repoStateUpdateChannel)
                .pipe(tap(update => repoSubject.next(update)))
                .subscribe();

            this.websocketConnections.set(domainKey, repoStateUpdateChannel);
            this.conflictSubjects.set(domainKey, repoSubject);

            return repoSubject as Observable<GitConflictState>;
        } else {
            return this.conflictSubjects[domainKey];
        }
    };

    private getDomainKey = () => {
        const [domainType, domainValue] = this.domain;
        return `${domainType === DomainType.PARTICIPATION ? 'participation' : 'test'}-${domainValue.id.toString()}`;
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryService extends DomainDependentEndpoint implements ICodeEditorRepositoryService {
    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
    }

    getStatus = () => {
        return this.http.get<any>(this.restResourceUrl!);
    };

    commit = () => {
        return this.http.post<void>(`${this.restResourceUrl}/commit`, {});
    };

    pull = () => {
        return this.http.get<void>(`${this.restResourceUrl}/pull`, {});
    };

    resetRepository = () => {
        return this.http.post<void>(`${this.restResourceUrl}/reset-repository`, {});
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorBuildLogService extends DomainDependentEndpoint implements IBuildLogService {
    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
    }

    getBuildLogs = () => {
        return this.http.get<BuildLogEntry[]>(`${this.restResourceUrl}/buildlogs`, {});
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryFileService extends DomainDependentEndpoint implements ICodeEditorRepositoryFileService, OnDestroy {
    private fileUpdateSubject = new Subject<{ [fileName: string]: string | null }>();
    private fileUpdateUrl: string;

    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        this.jhiWebsocketService.unsubscribe(this.fileUpdateUrl);
    }

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

    getRepositoryContent = () => {
        return this.http.get<{ [fileName: string]: FileType }>(`${this.restResourceUrl}/files`);
    };

    getFile = (fileName: string) => {
        return this.http.get(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).map(data => ({ fileContent: data }));
    };

    createFile = (fileName: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/file`, '', { params: new HttpParams().set('file', fileName) });
    };

    createFolder = (folderName: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/folder`, '', { params: new HttpParams().set('folder', folderName) });
    };

    updateFileContent = (fileName: string, fileContent: string) => {
        return this.http.put(`${this.restResourceUrl}/file`, fileContent, {
            params: new HttpParams().set('file', fileName),
        });
    };

    updateFiles = (fileUpdates: Array<{ fileName: string; fileContent: string }>) => {
        if (this.fileUpdateSubject) {
            this.fileUpdateSubject.complete();
        }
        if (this.fileUpdateUrl) {
            this.jhiWebsocketService.unsubscribe(this.fileUpdateUrl);
        }
        this.fileUpdateSubject = new Subject<{ [p: string]: string | null }>();

        this.jhiWebsocketService.subscribe(this.fileUpdateUrl);
        this.jhiWebsocketService.receive(this.fileUpdateUrl).subscribe(res => this.fileUpdateSubject.next(res), err => this.fileUpdateSubject.error(err));
        this.jhiWebsocketService.send(`${this.websocketResourceUrlSend}/files`, fileUpdates);
        return this.fileUpdateSubject as Observable<{ [fileName: string]: string | null }>;
    };

    renameFile = (currentFilePath: string, newFilename: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/rename-file`, { currentFilePath, newFilename });
    };

    deleteFile = (fileName: string) => {
        return this.http.delete<void>(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName) });
    };
}
