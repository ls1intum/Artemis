import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { BuildAgentInformation } from 'app/buildagent/shared/entities/build-agent-information.model';
import { catchError } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class BuildAgentsService {
    public adminResourceUrl = 'api/core/admin';

    private readonly http = inject(HttpClient);

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
        return this.http.put<void>(`${this.adminResourceUrl}/agents/${encodedAgentName}/pause`, null).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to pause build agent ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Pause All Build Agents
     */
    pauseAllBuildAgents(): Observable<void> {
        return this.http.put<void>(`${this.adminResourceUrl}/agents/pause-all`, null).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to pause build agents\n${err.message}`));
            }),
        );
    }

    /**
     * Resume Build Agent
     */
    resumeBuildAgent(agentName: string): Observable<void> {
        const encodedAgentName = encodeURIComponent(agentName);
        return this.http.put<void>(`${this.adminResourceUrl}/agents/${encodedAgentName}/resume`, null).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to resume build agent ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Resume all Build Agents
     */
    resumeAllBuildAgents(): Observable<void> {
        return this.http.put<void>(`${this.adminResourceUrl}/agents/resume-all`, null).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to resume build agents\n${err.message}`));
            }),
        );
    }

    /**
     * Clears distributed data. This includes BuildJobQueue, ProcessingJobs, resultQueue, build agent Information, docker image clean up.
     */
    clearDistributedData(): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/clear-distributed-data`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to clear distributed data\n${err.message}`));
            }),
        );
    }

    /**
     * Run the regular age + size cache cleanup on demand for the specified agent. Same task as the scheduled daily
     * cleanup, but invoked synchronously for one agent. The endpoint returns 204 immediately; the agent pauses,
     * prunes, and resumes asynchronously and reports progress via its periodic BuildAgentInformation push.
     */
    runCacheCleanup(agentName: string): Observable<void> {
        const encodedAgentName = encodeURIComponent(agentName);
        return this.http.put<void>(`${this.adminResourceUrl}/agents/${encodedAgentName}/run-cache-cleanup`, null).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to run cache cleanup on ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Wipe every file under the Maven dependency cache on the specified agent, regardless of age. Destructive —
     * intended for the admin Reclaim disk dialog when artifacts are suspected corrupt.
     */
    wipeMavenCache(agentName: string): Observable<void> {
        const encodedAgentName = encodeURIComponent(agentName);
        return this.http.delete<void>(`${this.adminResourceUrl}/agents/${encodedAgentName}/cache/maven`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to wipe Maven cache on ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Wipe every file under the Gradle dependency cache on the specified agent. Same semantics as
     * {@link wipeMavenCache}.
     */
    wipeGradleCache(agentName: string): Observable<void> {
        const encodedAgentName = encodeURIComponent(agentName);
        return this.http.delete<void>(`${this.adminResourceUrl}/agents/${encodedAgentName}/cache/gradle`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to wipe Gradle cache on ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Remove every Docker image not bound to a running container on the specified agent. Used to reclaim disk
     * when the agent is approaching full.
     */
    clearDockerImages(agentName: string): Observable<void> {
        const encodedAgentName = encodeURIComponent(agentName);
        return this.http.delete<void>(`${this.adminResourceUrl}/agents/${encodedAgentName}/docker-images`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to clear Docker images on ${agentName}\n${err.message}`));
            }),
        );
    }
}
