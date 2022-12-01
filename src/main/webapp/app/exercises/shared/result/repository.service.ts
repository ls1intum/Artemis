import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { FileType } from '../../programming/shared/code-editor/model/code-editor.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class RepositoryService {
    private resourceUrl = SERVER_API_URL + 'api/repository';

    constructor(private http: HttpClient) {}

    /**
     * Checks whether the participation data is clean or not.
     * @param participationId The identifier of the participation.
     */
    isClean(participationId: number): Observable<any> {
        return this.http.get<any>(`${this.resourceUrl}/${participationId}`).pipe(map((data) => ({ isClean: data.isClean })));
    }

    /**
     * Commits to certain participation.
     * @param participationId The identifier of the participation.
     */
    commit(participationId: number): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/commit`, {});
    }

    /**
     * Pulls from a certain participation.
     * @param participationId The identifier of the participation.
     */
    pull(participationId: number): Observable<void> {
        return this.http.get<void>(`${this.resourceUrl}/${participationId}/pull`, {});
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

    /**
     * Get files of a specific participation.
     * @param participationId The identifier of the participation.
     */
    query(participationId: number): Observable<{ [fileName: string]: FileType }> {
        return this.http.get<{ [fileName: string]: FileType }>(`${this.resourceUrl}/${participationId}/files`);
    }

    /**
     * Get a specific file from a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be obtained.
     */
    get(participationId: number, fileName: string): Observable<any> {
        return this.http
            .get(`${this.resourceUrl}/${participationId}/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' })
            .pipe(map((data) => ({ fileContent: data })));
    }

    /**
     * Update a file in a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be updated.
     * @param fileContent The content of the file.
     */
    update(participationId: number, fileName: string, fileContent: string): Observable<any> {
        return this.http.put(`${this.resourceUrl}/${participationId}/file`, fileContent, {
            params: new HttpParams().set('file', fileName),
        });
    }

    /**
     * Create a file in a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be created.
     */
    createFile(participationId: number, fileName: string): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/file`, '', { params: new HttpParams().set('file', fileName) });
    }

    /**
     * Create a folder in a specific participation.
     * @param participationId The identifier of the participation.
     * @param folderName The name of the folder to be created.

     */
    createFolder(participationId: number, folderName: string): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/folder`, '', { params: new HttpParams().set('folder', folderName) });
    }

    /**
     * Rename a file in a specific participation.
     * @param participationId The identifier of the participation.
     * @param currentFilePath The path of the file to be renamed.
     * @param newFilename The new name of the file.

     */
    rename(participationId: number, currentFilePath: string, newFilename: string): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${participationId}/rename-file`, { currentFilePath, newFilename });
    }

    /**
     * Delete a file from a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be deleted.
     */
    delete(participationId: number, fileName: string): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/${participationId}/file`, { params: new HttpParams().set('file', fileName) });
    }
}
