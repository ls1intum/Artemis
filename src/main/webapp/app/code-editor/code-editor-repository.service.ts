import { HttpClient, HttpEvent, HttpHandler, HttpInterceptor, HttpParams, HttpRequest } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Subject, Subscription } from 'rxjs';
import { share } from 'rxjs/operators';
import { Observable } from 'rxjs/Observable';
import { tap } from 'rxjs/operators';

import { BuildLogEntryArray } from 'app/entities/build-log';
import { SERVER_API_URL } from 'app/app.constants';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { Participation } from 'app/entities/participation';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Injectable({ providedIn: 'root' })
export class DomainService<T> {
    protected domain: T;
    private subject = new Subject<T>();
    private domainChange = this.subject.pipe(share());

    // constructor(private httpClient: HttpClient) {
    //     console.log('test');
    // }

    public setDomain(domain: T) {
        this.domain = domain;
        this.subject.next(domain);
    }
    public getDomain() {
        return this.domain;
    }
    public subscribeDomainChange(): Observable<T> {
        return this.domainChange as Observable<T>;
    }
}

abstract class DomainDependent<T> implements OnDestroy {
    protected domain: T;
    private domainChangeSubscription: Subscription;

    constructor(private domainService: DomainService<T>) {
        this.domainChangeSubscription = this.domainService
            .subscribeDomainChange()
            .pipe(tap(domain => this.setDomain(domain)))
            .subscribe();
    }
    protected setDomain(domain: T) {
        this.domain = domain;
    }
    ngOnDestroy() {
        if (this.domainChangeSubscription) {
            this.domainChangeSubscription.unsubscribe();
        }
    }
}

export abstract class IRepositoryFileService<T> extends DomainDependent<T> {
    abstract getRepositoryContent: () => Observable<{ [fileName: string]: FileType }>;
    abstract getFile: (fileName: string) => Observable<{ fileContent: string }>;
    abstract createFile: (fileName: string) => Observable<void>;
    abstract createFolder: (folderName: string) => Observable<void>;
    abstract updateFileContent: (fileName: string, fileContent: string) => Observable<Object>;
    abstract renameFile: (filePath: string, newFileName: string) => Observable<void>;
    abstract deleteFile: (filePath: string) => Observable<void>;

    constructor(domainChangeService: DomainService<T>) {
        super(domainChangeService);
    }
}

export abstract class IRepositoryService<T> extends DomainDependent<T> {
    abstract isClean: () => Observable<{ isClean: boolean }>;
    abstract commit: () => Observable<void>;
    abstract pull: () => Observable<void>;
}

export interface IRepositoryBuildableService {
    getBuildLogs: () => Observable<BuildLogEntryArray>;
}

@Injectable({ providedIn: 'root' })
export class RepositoryParticipationService extends IRepositoryService<Participation> implements IRepositoryBuildableService {
    private resourceUrlBase = `${SERVER_API_URL}/api/repository`;
    private resourceUrl: string;

    constructor(private domainChangeService: DomainService<Participation>, protected http: HttpClient) {
        super(domainChangeService);
    }

    setDomain(participation: Participation) {
        super.setDomain(participation);
        this.resourceUrl = `${this.resourceUrlBase}/${this.domain.id}`;
    }

    isClean = () => {
        return this.http.get<any>(this.resourceUrl).map(data => ({ isClean: data.isClean }));
    };

    commit = () => {
        return this.http.post<void>(`${this.resourceUrl}/commit`, {});
    };

    pull = () => {
        return this.http.get<void>(`${this.resourceUrl}/pull`, {});
    };

    getBuildLogs = () => {
        return this.http.get<BuildLogEntryArray>(`${this.resourceUrl}/buildlogs`);
    };
}

@Injectable({ providedIn: 'root' })
export class RepositoryFileParticipationService extends IRepositoryFileService<Participation> {
    private resourceUrlBase = `${SERVER_API_URL}/api/repository`;
    private resourceUrl: string;

    constructor(private domainChangeService: DomainService<Participation>, protected http: HttpClient) {
        super(domainChangeService);
    }

    setDomain(participation: Participation) {
        super.setDomain(participation);
        this.resourceUrl = `${this.resourceUrlBase}/${this.domain.id}`;
    }

    getRepositoryContent = () => {
        return this.http.get<{ [fileName: string]: FileType }>(`${this.resourceUrl}/files`);
    };

    getFile = (fileName: string) => {
        return this.http.get(`${this.resourceUrl}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).map(data => ({ fileContent: data }));
    };

    createFile = (fileName: string) => {
        return this.http.post<void>(`${this.resourceUrl}/file`, '', { params: new HttpParams().set('file', fileName) });
    };

    createFolder = (folderName: string) => {
        return this.http.post<void>(`${this.resourceUrl}/folder`, '', { params: new HttpParams().set('folder', folderName) });
    };

    updateFileContent = (fileName: string, fileContent: string) => {
        return this.http.put(`${this.resourceUrl}/file`, fileContent, {
            params: new HttpParams().set('file', fileName),
        });
    };

    renameFile = (currentFilePath: string, newFilename: string) => {
        return this.http.post<void>(`${this.resourceUrl}/rename-file`, { currentFilePath, newFilename });
    };

    deleteFile = (fileName: string) => {
        return this.http.delete<void>(`${this.resourceUrl}/file`, { params: new HttpParams().set('file', fileName) });
    };
}

@Injectable({ providedIn: 'root' })
export class TestRepositoryService extends IRepositoryService<ProgrammingExercise> {
    private resourceUrlBase = `${SERVER_API_URL}/api/test-repository`;
    private resourceUrl: string;

    constructor(private domainChangeService: DomainService<ProgrammingExercise>, protected http: HttpClient) {
        super(domainChangeService);
    }

    setDomain(exercise: ProgrammingExercise) {
        super.setDomain(exercise);
        this.resourceUrl = `${this.resourceUrlBase}/${this.domain.id}`;
    }

    isClean = () => {
        return this.http.get<any>(this.resourceUrl).map(data => ({ isClean: data.isClean }));
    };

    commit = () => {
        return this.http.post<void>(`${this.resourceUrl}/commit`, {});
    };

    pull = () => {
        return this.http.get<void>(`${this.resourceUrl}/pull`, {});
    };
}

@Injectable({ providedIn: 'root' })
export class TestRepositoryFileService extends IRepositoryFileService<ProgrammingExercise> {
    private resourceUrlBase = `${SERVER_API_URL}/api/test-repository`;
    private resourceUrl: string;

    constructor(private domainChangeService: DomainService<ProgrammingExercise>, protected http: HttpClient) {
        super(domainChangeService);
    }

    setDomain(exercise: ProgrammingExercise) {
        super.setDomain(exercise);
        this.resourceUrl = `${this.resourceUrlBase}/${this.domain.id}`;
    }

    getRepositoryContent = () => {
        return this.http.get<{ [fileName: string]: FileType }>(`${this.resourceUrl}/files`);
    };

    getFile = (fileName: string) => {
        return this.http.get(`${this.resourceUrl}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' }).map(data => ({ fileContent: data }));
    };

    createFile = (fileName: string) => {
        return this.http.post<void>(`${this.resourceUrl}/file`, '', { params: new HttpParams().set('file', fileName) });
    };

    createFolder = (folderName: string) => {
        return this.http.post<void>(`${this.resourceUrl}/folder`, '', { params: new HttpParams().set('folder', folderName) });
    };

    updateFileContent = (fileName: string, fileContent: string) => {
        return this.http.put(`${this.resourceUrl}/file`, fileContent, {
            params: new HttpParams().set('file', fileName),
        });
    };

    renameFile = (currentFilePath: string, newFilename: string) => {
        return this.http.post<void>(`${this.resourceUrl}/rename-file`, { currentFilePath, newFilename });
    };

    deleteFile = (fileName: string) => {
        return this.http.delete<void>(`${this.resourceUrl}/file`, { params: new HttpParams().set('file', fileName) });
    };
}
