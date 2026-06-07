import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Injectable({ providedIn: 'root' })
export class RepositoryService {
    private http = inject(HttpClient);

    /**
     * Checks whether the participation data is clean or not.
     * @param participationId The identifier of the participation.
     */
    isClean(participationId: number): Observable<any> {
        return this.http.get<any>(`api/programming/participations/${participationId}/repository`).pipe(map((data) => ({ isClean: data.isClean })));
    }

    /**
     * Commits to certain participation.
     * @param participationId The identifier of the participation.
     */
    commit(participationId: number): Observable<void> {
        return this.http.post<void>(`api/programming/participations/${participationId}/repository/commit`, {});
    }

    /**
     * Pulls from a certain participation.
     * @param participationId The identifier of the participation.
     */
    pull(participationId: number): Observable<void> {
        return this.http.get<void>(`api/programming/participations/${participationId}/repository/pull`, {});
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
    private http = inject(HttpClient);

    /**
     * Get files of a specific participation.
     * @param participationId The identifier of the participation.
     */
    query(participationId: number): Observable<{ [fileName: string]: FileType }> {
        return this.http.get<{ [fileName: string]: FileType }>(`api/programming/participations/${participationId}/repository/files`);
    }

    /**
     * Get a specific file from a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be obtained.
     */
    get(participationId: number, fileName: string): Observable<any> {
        return this.http
            .get(`api/programming/participations/${participationId}/repository/file`, { params: new HttpParams().set('file', fileName), responseType: 'text' })
            .pipe(map((data) => ({ fileContent: data })));
    }

    /**
     * Update a file in a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be updated.
     * @param fileContent The content of the file.
     */
    update(participationId: number, fileName: string, fileContent: string): Observable<any> {
        return this.http.put(`api/programming/participations/${participationId}/repository/file`, fileContent, {
            params: new HttpParams().set('file', fileName),
        });
    }

    /**
     * Create a file in a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be created.
     */
    createFile(participationId: number, fileName: string): Observable<void> {
        return this.http.post<void>(`api/programming/participations/${participationId}/repository/file`, '', { params: new HttpParams().set('file', fileName) });
    }

    /**
     * Create a folder in a specific participation.
     * @param participationId The identifier of the participation.
     * @param folderName The name of the folder to be created.

     */
    createFolder(participationId: number, folderName: string): Observable<void> {
        return this.http.post<void>(`api/programming/participations/${participationId}/repository/folder`, '', { params: new HttpParams().set('folder', folderName) });
    }

    /**
     * Rename a file in a specific participation.
     * @param participationId The identifier of the participation.
     * @param currentFilePath The path of the file to be renamed.
     * @param newFilename The new name of the file.

     */
    rename(participationId: number, currentFilePath: string, newFilename: string): Observable<void> {
        return this.http.post<void>(`api/programming/participations/${participationId}/repository/rename-file`, { currentFilePath, newFilename });
    }

    /**
     * Delete a file from a specific participation.
     * @param participationId The identifier of the participation.
     * @param fileName The name of the file to be deleted.
     */
    delete(participationId: number, fileName: string): Observable<void> {
        return this.http.delete<void>(`api/programming/participations/${participationId}/repository/file`, { params: new HttpParams().set('file', fileName) });
    }
}
