import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { Observable } from 'rxjs/Observable';

import { BuildLogEntryArray, BuildLogEntry } from 'app/entities/build-log';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { JhiWebsocketService } from 'app/core';
import { DomainChange, DomainDependentEndpoint, DomainService } from 'app/code-editor/service';

export enum DomainType {
    PARTICIPATION = 'PARTICIPATION',
    TEST_REPOSITORY = 'TEST_REPOSITORY',
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
    isClean: () => Observable<{ isClean: boolean }>;
    commit: () => Observable<void>;
    pull: () => Observable<void>;
}

export interface IBuildLogService {
    getBuildLogs: () => Observable<BuildLogEntry[]>;
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryService extends DomainDependentEndpoint implements ICodeEditorRepositoryService {
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
        return this.http.get<BuildLogEntry[]>(`${this.restResourceUrl}/buildlogs`, {});
    };
}

@Injectable({ providedIn: 'root' })
export class CodeEditorRepositoryFileService extends DomainDependentEndpoint implements ICodeEditorRepositoryFileService {
    constructor(http: HttpClient, jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(http, jhiWebsocketService, domainService);
    }

    setDomain(domain: DomainChange) {
        super.setDomain(domain);
        this.jhiWebsocketService.subscribe(`${this.websocketResourceUrlReceive}/files`);
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
        const subject = new Subject<{ [fileName: string]: string | null }>();
        this.jhiWebsocketService.send(`${this.websocketResourceUrlSend}/files`, fileUpdates);
        this.jhiWebsocketService.receive(`${this.websocketResourceUrlReceive}/files`).subscribe(res => subject.next(res), err => subject.error(err));
        return subject;
    };

    renameFile = (currentFilePath: string, newFilename: string) => {
        return this.http.post<void>(`${this.restResourceUrl}/rename-file`, { currentFilePath, newFilename });
    };

    deleteFile = (fileName: string) => {
        return this.http.delete<void>(`${this.restResourceUrl}/file`, { params: new HttpParams().set('file', fileName) });
    };
}
