import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { SERVER_API_URL } from 'app/app.constants';
import { BuildLogEntry } from 'app/entities/build-log.model';

export interface IBuildLogService {
    getBuildLogs: (participationId: number) => Observable<BuildLogEntry[]>;
}

@Injectable({ providedIn: 'root' })
export class BuildLogService implements IBuildLogService {
    private restResourceUrlBase = `${SERVER_API_URL}/api`;
    private assignmentResourceUrl = `${this.restResourceUrlBase}/repository`;

    constructor(private http: HttpClient) {}

    /**
     * Retrieves the build logs for a given participation.
     * @param participationId The identifier of the participation.
     */
    getBuildLogs(participationId: number): Observable<BuildLogEntry[]> {
        return this.http.get<BuildLogEntry[]>(`${this.assignmentResourceUrl}/${participationId}/buildlogs`);
    }
}
