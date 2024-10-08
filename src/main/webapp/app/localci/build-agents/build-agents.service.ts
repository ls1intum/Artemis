import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { BuildAgent } from 'app/entities/programming/build-agent.model';
import { catchError } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class BuildAgentsService {
    private http = inject(HttpClient);

    public adminResourceUrl = 'api/admin';

    /**
     * Get all build agents
     */
    getBuildAgentSummary(): Observable<BuildAgent[]> {
        return this.http.get<BuildAgent[]>(`${this.adminResourceUrl}/build-agents`);
    }

    /**
     * Get build agent details
     */
    getBuildAgentDetails(agentName: string): Observable<BuildAgent> {
        return this.http.get<BuildAgent>(`${this.adminResourceUrl}/build-agent`, { params: { agentName } }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to fetch build agent details ${agentName}\n${err.message}`));
            }),
        );
    }
}
