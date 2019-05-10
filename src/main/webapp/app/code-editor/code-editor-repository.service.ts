import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Subscription, BehaviorSubject } from 'rxjs';
import { share } from 'rxjs/operators';
import { Observable } from 'rxjs/Observable';
import { filter, tap } from 'rxjs/operators';

import { BuildLogEntryArray } from 'app/entities/build-log';
import { SERVER_API_URL } from 'app/app.constants';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { Participation } from 'app/entities/participation';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { JhiWebsocketService } from 'app/core';

export enum DomainType {
    PARTICIPATION = 'PARTICIPATION',
    TEST_REPOSITORY = 'TEST_REPOSITORY',
}

export type DomainParticipationChange = [DomainType.PARTICIPATION, Participation];
export type DomainTestRepositoryChange = [DomainType.TEST_REPOSITORY, ProgrammingExercise];
export type DomainChange = DomainParticipationChange | DomainTestRepositoryChange;

@Injectable({ providedIn: 'root' })
export class DomainService {
    protected domain: DomainChange;
    private subject = new BehaviorSubject<DomainParticipationChange | DomainTestRepositoryChange>(null);
    // private domainChange = this.subject.pipe(share());

    public setDomain(domain: DomainChange) {
        this.domain = domain;
        this.subject.next(domain);
    }
    public getDomain() {
        return this.domain;
    }
    public subscribeDomainChange(): Observable<DomainChange> {
        return this.subject;
        // return this.domainChange as Observable<DomainChange>;
    }
}

export abstract class DomainDependent implements OnDestroy {
    protected domain: DomainChange;
    protected domainChangeSubscription: Subscription;

    constructor(private domainService: DomainService) {}

    initDomainSubscription() {
        this.domainChangeSubscription = this.domainService
            .subscribeDomainChange()
            .pipe(
                filter(domain => !!domain),
                tap((domain: DomainChange) => {
                    console.log({ domain });
                    this.setDomain(domain);
                }),
            )
            .subscribe();
    }

    setDomain(domain: DomainChange) {
        this.domain = domain;
    }

    ngOnDestroy() {
        if (this.domainChangeSubscription) {
            this.domainChangeSubscription.unsubscribe();
        }
    }
}

export abstract class DomainDependentEndpoint extends DomainDependent implements OnDestroy {
    private restResourceUrlBase = `${SERVER_API_URL}/api`;
    protected restResourceUrl: string;
    private websocketResourceUrlBase = '/topic';
    protected websocketResourceUrlSend: string;
    protected websocketResourceUrlReceive: string;

    constructor(protected http: HttpClient, protected jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(domainService);
        this.initDomainSubscription();
    }

    setDomain(domain: DomainChange) {
        super.setDomain(domain);
        const [domainType, domainValue] = this.domain;
        if (this.websocketResourceUrlSend) {
            this.jhiWebsocketService.unsubscribe(this.websocketResourceUrlSend);
        }
        if (this.websocketResourceUrlReceive) {
            this.jhiWebsocketService.unsubscribe(this.websocketResourceUrlReceive);
        }
        switch (domainType) {
            case DomainType.PARTICIPATION:
                this.restResourceUrl = `${this.restResourceUrlBase}/repository/${domainValue.id}`;
                this.websocketResourceUrlSend = `${this.websocketResourceUrlBase}/repository/${domainValue.id}`;
                this.websocketResourceUrlReceive = `user/${this.websocketResourceUrlSend}`;
                break;
            case DomainType.TEST_REPOSITORY:
                this.restResourceUrl = `${this.restResourceUrlBase}/test-repository/${domainValue.id}`;
                this.websocketResourceUrlSend = `${this.websocketResourceUrlBase}/test-repository/${domainValue.id}`;
                this.websocketResourceUrlReceive = `user/${this.websocketResourceUrlSend}`;
                break;
            default:
                this.restResourceUrl = null;
                this.websocketResourceUrlSend = null;
                this.websocketResourceUrlReceive = null;
        }
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        if (this.websocketResourceUrlSend) {
            this.jhiWebsocketService.unsubscribe(this.websocketResourceUrlSend);
        }
        if (this.websocketResourceUrlReceive) {
            this.jhiWebsocketService.unsubscribe(this.websocketResourceUrlReceive);
        }
    }
}

export interface IRepositoryFileService {
    getRepositoryContent: () => Observable<{ [fileName: string]: FileType }>;
    getFile: (fileName: string) => Observable<{ fileContent: string }>;
    createFile: (fileName: string) => Observable<void>;
    createFolder: (folderName: string) => Observable<void>;
    updateFileContent: (fileName: string, fileContent: string) => Observable<Object>;
    // TODO: Implement websocket call
    updateFiles: (fileUpdates: Array<{ fileName: string; fileContent: string }>) => Observable<Array<[string, string]>>;
    renameFile: (filePath: string, newFileName: string) => Observable<void>;
    deleteFile: (filePath: string) => Observable<void>;
}

export interface IRepositoryService {
    isClean: () => Observable<{ isClean: boolean }>;
    commit: () => Observable<void>;
    pull: () => Observable<void>;
}

export interface IBuildLogService {
    getBuildLogs: () => Observable<BuildLogEntryArray>;
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryService extends DomainDependentEndpoint implements IRepositoryService {
    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
    }

    isClean = () => {
        return this.http.get<any>(this.restResourceUrl).map(data => ({ isClean: data.isClean }));
    };

    commit = () => {
        return this.http.post<void>(`${this.restResourceUrl}/commit`, {});
    };

    pull = () => {
        return this.http.get<void>(`${this.restResourceUrl}/pull`, {});
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorBuildLogService extends DomainDependentEndpoint implements IBuildLogService {
    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
    }

    getBuildLogs = () => {
        return this.http.get<BuildLogEntryArray>(`${this.restResourceUrl}/buildlogs`, {});
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryFileService extends DomainDependentEndpoint implements IRepositoryFileService {
    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
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
        const subject = new Subject<Array<[string, string]>>();
        this.jhiWebsocketService.send(`${this.websocketResourceUrlSend}/files`, fileUpdates);
        this.jhiWebsocketService.receive(`${this.websocketResourceUrlReceive}/files`).subscribe(res => subject.next(res), err => subject.error(err));
        return subject as Observable<Array<[string, string]>>;
    };

    renameFile = (currentFilePath: string, newFilename: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/rename-file`, { currentFilePath, newFilename });
    };

    deleteFile = (fileName: string) => {
        return this.http.delete<void>(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName) });
    };
}
