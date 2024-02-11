import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BuildAgent } from 'app/entities/build-agent.model';

@Injectable({ providedIn: 'root' })
export class BuildAgentsService {
    public adminResourceUrl = 'api/admin';

    constructor(private http: HttpClient) {}

    /**
     * Get all build agents
     */
    getBuildAgents(): Observable<BuildAgent[]> {
        return this.http.get<BuildAgent[]>(`${this.adminResourceUrl}/build-agents`);
    }
}
