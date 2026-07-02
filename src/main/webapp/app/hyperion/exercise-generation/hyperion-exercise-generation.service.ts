import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import {
    HyperionGenerationJobStart,
    HyperionGenerationMessage,
    HyperionGenerationRequest,
    HyperionGenerationStatus,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/**
 * Drives the agentic whole-exercise generation/adaptation run for the editor: starting a run in an explicit mode, fetching the current run status for reconnect, subscribing to the
 * live progress + file-snapshot stream, requesting cancellation, and reverting the last in-place adaptation. One endpoint and one engine back both {@code GENERATE} and {@code ADAPT}.
 */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseGenerationService {
    private readonly http = inject(HttpClient);
    private readonly websocketService = inject(WebsocketService);

    private readonly resourceUrl = 'api/hyperion/programming-exercises';

    /**
     * Starts an agentic whole-exercise generation/adaptation run in the request's explicit mode and returns the started job id (also the websocket topic suffix).
     * @param exerciseId the exercise id
     * @param request the explicit mode plus the optional prompt / selected feedback threads
     */
    generate(exerciseId: number, request: HyperionGenerationRequest): Observable<HyperionGenerationJobStart> {
        return this.http.post<HyperionGenerationJobStart>(`${this.resourceUrl}/${exerciseId}/generate-exercise`, request);
    }

    /**
     * Returns the current or most-recent run for the exercise so a (re)connecting client can replay the transcript, rehydrate the file preview, and decide whether to keep listening.
     * A 204 (no retained run for this user) surfaces as a response with a `null`/absent body.
     * @param exerciseId the exercise id
     */
    getStatus(exerciseId: number): Observable<HttpResponse<HyperionGenerationStatus>> {
        return this.http.get<HyperionGenerationStatus>(`${this.resourceUrl}/${exerciseId}/generate-exercise/status`, { observe: 'response' });
    }

    /**
     * Requests cooperative cancellation of a running job (owner only).
     * @param exerciseId the exercise id
     * @param jobId the running job id
     */
    cancel(exerciseId: number, jobId: string): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/generate-exercise/jobs/${jobId}`);
    }

    /**
     * Reverts the last in-place adaptation of the exercise, resetting its repositories back to the commit state captured at the start of that adaptation run.
     * @param exerciseId the exercise id
     */
    revertAdaptation(exerciseId: number): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${exerciseId}/generate-exercise/revert-adaptation`, undefined);
    }

    /**
     * Subscribes to the live stream (progress events + whole-file snapshots) for a job, delivered on the owner's private topic.
     * @param jobId the job id whose stream to subscribe to
     */
    subscribeToStream(jobId: string): Observable<HyperionGenerationMessage> {
        return this.websocketService.subscribe<HyperionGenerationMessage>(`/user/topic/hyperion/exercise-generation/jobs/${jobId}`);
    }
}
