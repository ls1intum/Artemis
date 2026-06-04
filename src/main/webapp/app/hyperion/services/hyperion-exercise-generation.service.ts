import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ExerciseGenerationEvent } from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';

export interface ExerciseGenerationJobStart {
    jobId: string;
}

/**
 * The reconnection view of a generation run: its id, whether it is still running, and the events so far to replay. Mirrors the server-side {@code ExerciseGenerationStatusDTO}.
 */
export interface ExerciseGenerationStatus {
    jobId: string;
    running: boolean;
    events: ExerciseGenerationEvent[];
}

/**
 * REST client for the agentic whole-exercise generation endpoints (matches {@code HyperionExerciseGenerationResource}). Hand-written; TODO replace with the generated OpenAPI client once these endpoints are included.
 */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseGenerationService {
    private http = inject(HttpClient);

    private resourceUrl(exerciseId: number): string {
        return `api/hyperion/programming-exercises/${exerciseId}/generate-exercise`;
    }

    /**
     * Starts an agentic generation/adaptation run. The {@code prompt} is an optional brief (create) or feedback (adapt); the server applies a default when omitted.
     */
    generateExercise(exerciseId: number, prompt?: string): Observable<ExerciseGenerationJobStart> {
        return this.http.post<ExerciseGenerationJobStart>(this.resourceUrl(exerciseId), { prompt });
    }

    /**
     * Returns the caller's current or most-recent run for the exercise (id, running flag, and transcript to replay), or {@code undefined} when there is nothing to show. Used on
     * (re)load to reattach to a live run or display the last outcome.
     */
    getStatus(exerciseId: number): Observable<ExerciseGenerationStatus | undefined> {
        // The endpoint returns 204 (empty body) when no run is retained; HttpClient surfaces that as a null body, which we normalise to undefined.
        return this.http.get<ExerciseGenerationStatus | null>(`${this.resourceUrl(exerciseId)}/status`).pipe(map((status) => status ?? undefined));
    }

    /**
     * Requests cooperative cancellation of a running generation job; the server aborts between agent turns and before the verification build.
     */
    cancel(exerciseId: number, jobId: string): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl(exerciseId)}/jobs/${jobId}`);
    }
}
