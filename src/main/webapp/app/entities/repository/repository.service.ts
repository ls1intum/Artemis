import { HttpClient, HttpEvent, HttpHandler, HttpInterceptor, HttpParams, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Observable } from 'rxjs/Observable';
import { FileType } from '../../code-editor/model/code-editor.model';
import { BuildLogEntry } from 'app/entities/build-log/build-log.model';
import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class RepositoryService {
    private resourceUrl = SERVER_API_URL + 'api/repository';

    constructor(private http: HttpClient) {}

    isClean(participationId: number): Observable<any> {
        return this.http.get<any>(`${this.resourceUrl}/${participationId}`).map(data => ({ isClean: data.isClean }));
    }

    commit(participationId: number): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/commit`, {});
    }

    pull(participationId: number): Observable<void> {
        return this.http.get<void>(`${this.resourceUrl}/${participationId}/pull`, {});
    }

    buildlogs(participationId: number): Observable<BuildLogEntry[]> {
        return this.http.get<BuildLogEntry[]>(`${this.resourceUrl}/${participationId}/buildlogs`);
    }
}

export interface IRepositoryFileService {
    query: (participationId: number) => Observable<{ [fileName: string]: FileType }>;
    get: (participationId: number, fileName: string) => Observable<any>;

    update: (participationId: number, fileName: string, fileContent: string) => Observable<any>;

    createFile: (participationId: number, fileName: string) => Observable<void>;

    createFolder: (participationId: number, folderName: string) => Observable<void>;

    rename: (participationId: number, currentFilePath: string, newFilename: string) => Observable<void>;

    delete: (participationId: number, fileName: string) => Observable<void>;
}

@Injectable({ providedIn: 'root' })
export class RepositoryFileService implements IRepositoryFileService {
    private resourceUrl = SERVER_API_URL + 'api/repository';

    constructor(private http: HttpClient) {}

    query(participationId: number): Observable<{ [fileName: string]: FileType }> {
        return this.http.get<{ [fileName: string]: FileType }>(`${this.resourceUrl}/${participationId}/files`);
    }

    get(participationId: number, fileName: string): Observable<any> {
        return this.http
            .get(`${this.resourceUrl}/${participationId}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' })
            .map(data => ({ fileContent: data }));
    }

    update(participationId: number, fileName: string, fileContent: string): Observable<any> {
        return this.http.put(`${this.resourceUrl}/${participationId}/file`, fileContent, {
            params: new HttpParams().set('file', fileName),
        });
    }

    createFile(participationId: number, fileName: string): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/file`, '', { params: new HttpParams().set('file', fileName) });
    }

    createFolder(participationId: number, folderName: string): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/folder`, '', { params: new HttpParams().set('folder', folderName) });
    }

    rename(participationId: number, currentFilePath: string, newFilename: string): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/rename-file`, { currentFilePath, newFilename });
    }

    delete(participationId: number, fileName: string): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/${participationId}/file`, { params: new HttpParams().set('file', fileName) });
    }
}

@Injectable()
export class RepositoryInterceptor implements HttpInterceptor {
    constructor(private localStorage: LocalStorageService, private sessionStorage: SessionStorageService) {}

    // TODO: check why the auth.interceptor.ts does not add the authorization header
    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        const token = this.localStorage.retrieve('authenticationToken') || this.sessionStorage.retrieve('authenticationToken');
        if (!!token) {
            const authReq = req.clone({
                headers: req.headers.set('Authorization', 'Bearer ' + token),
            });
            return next.handle(authReq);
        }
        return next.handle(req);
    }
}
