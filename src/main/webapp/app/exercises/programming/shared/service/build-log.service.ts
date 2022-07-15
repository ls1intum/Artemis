import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BuildLogEntry } from 'app/entities/build-log.model';

export interface IBuildLogService {
    getBuildLogs: (participationId: number, resultId?: number) => Observable<BuildLogEntry[]>;
}

@Injectable({ providedIn: 'root' })
export class BuildLogService implements IBuildLogService {
    private restResourceUrlBase = `${SERVER_API_URL}api`;
    private assignmentResourceUrl = `${this.restResourceUrlBase}/repository`;

    constructor(private http: HttpClient) {}

    /**
     * Retrieves the build logs for a given participation and optionally, a given result.
     * @param participationId The identifier of the participation.
     * @param resultId The identifier of an optional result to specify which submission to use
     */
    getBuildLogs(participationId: number, resultId?: number): Observable<BuildLogEntry[]> {
        let params = new HttpParams();
        if (resultId) {
            params = params.set('resultId', resultId);
        }
        return this.http.get<BuildLogEntry[]>(`${this.assignmentResourceUrl}/${participationId}/buildlogs`, { params });
    }
}
