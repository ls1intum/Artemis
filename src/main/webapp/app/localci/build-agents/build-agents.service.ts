import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { BuildAgentInformation } from 'app/entities/programming/build-agent-information.model';
import { catchError } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class BuildAgentsService {
    public adminResourceUrl = 'api/admin';

    constructor(private http: HttpClient) {}

    /**
     * Get all build agents
     */
    getBuildAgentSummary(): Observable<BuildAgentInformation[]> {
        return this.http.get<BuildAgentInformation[]>(`${this.adminResourceUrl}/build-agents`);
    }

    /**
     * Get build agent details
     */
    getBuildAgentDetails(agentName: string): Observable<BuildAgentInformation> {
        return this.http.get<BuildAgentInformation>(`${this.adminResourceUrl}/build-agent`, { params: { agentName } }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to fetch build agent details ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Pause Build Agent
     */
    pauseBuildAgent(agentName: string): Observable<void> {
        const encodedAgentName = encodeURIComponent(agentName);
        return this.http.put<void>(`${this.adminResourceUrl}/agent/${encodedAgentName}/pause`, null).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to pause build agent ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Resume Build Agent
     */
    resumeBuildAgent(agentName: string): Observable<void> {
        const encodedAgentName = encodeURIComponent(agentName);
        return this.http.put<void>(`${this.adminResourceUrl}/agent/${encodedAgentName}/resume`, null).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to resume build agent ${agentName}\n${err.message}`));
            }),
        );
    }
}
