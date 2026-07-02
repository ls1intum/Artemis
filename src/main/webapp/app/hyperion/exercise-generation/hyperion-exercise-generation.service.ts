import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { HyperionGenerationMessage, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/**
 * Drives the agentic whole-exercise generation/adaptation run for the editor: fetching the current run status for reconnect, subscribing to the live progress + file-snapshot
 * stream, and requesting cancellation.
 */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseGenerationService {
    private readonly http = inject(HttpClient);
    private readonly websocketService = inject(WebsocketService);

    private readonly resourceUrl = 'api/hyperion/programming-exercises';

    /**
     * Returns the current or most-recent run for the exercise so a (re)connecting client can replay the transcript, rehydrate the file preview, and decide whether to keep listening.
     * A 204 (no retained run for this user) surfaces as a response with a `null`/absent body.
     * @param exerciseId the exercise id
     */
    getStatus(exerciseId: number): Observable<HttpResponse<HyperionGenerationStatus>> {
        return this.http.get<HyperionGenerationStatus>(`${this.resourceUrl}/${exerciseId}/generation-jobs/status`, { observe: 'response' });
    }

    /**
     * Requests cooperative cancellation of a running job (owner only).
     * @param exerciseId the exercise id
     * @param jobId the running job id
     */
    cancel(exerciseId: number, jobId: string): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/${exerciseId}/generation-jobs/${jobId}/cancel`, undefined);
    }

    /**
     * Subscribes to the live stream (progress events + whole-file snapshots) for a job, delivered on the owner's private topic.
     * @param jobId the job id whose stream to subscribe to
     */
    subscribeToStream(jobId: string): Observable<HyperionGenerationMessage> {
        return this.websocketService.subscribe<HyperionGenerationMessage>(`/user/topic/hyperion/exercise-generation/jobs/${jobId}`);
    }
}
