import { Injectable, OnDestroy, inject } from '@angular/core';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

export type ExerciseGenerationCompletionStatus = 'SUCCESS' | 'PARTIAL';

/**
 * The structured verification verdict carried on a terminal event, so the client can render scannable chips instead of parsing prose. Mirrors {@code ExerciseGenerationEventDTO.Verdict}.
 */
export interface ExerciseGenerationVerdict {
    accepted: boolean;
    solutionPassed: boolean;
    templateFailed: boolean;
    testCount: number;
    reasons: string[];
}

/**
 * A progress event streamed while an agentic whole-exercise generation/adaptation runs. Mirrors the server-side {@code ExerciseGenerationEventDTO}.
 */
export interface ExerciseGenerationEvent {
    type: 'STARTED' | 'PROGRESS' | 'DONE' | 'CANCELLED' | 'ERROR';
    message?: string;
    completionStatus?: ExerciseGenerationCompletionStatus;
    verdict?: ExerciseGenerationVerdict;
    timestamp?: string;
}

type SubscribedJob = { wsSubscription: Subscription; subject: Subject<ExerciseGenerationEvent> };

/**
 * Subscribes to the websocket channel for an agentic exercise-generation job and exposes its progress as an observable stream.
 */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseGenerationWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);
    private subscribedJobs = new Map<string, SubscribedJob>();

    /**
     * Subscribes to an exercise-generation job channel.
     * @param jobId the job identifier
     * @returns an observable stream of job events
     */
    subscribeToJob(jobId: string): Observable<ExerciseGenerationEvent> {
        const existing = this.subscribedJobs.get(jobId);
        if (existing) {
            return existing.subject.asObservable();
        }
        const subject = new Subject<ExerciseGenerationEvent>();
        const ws$ = this.websocketService.subscribe<ExerciseGenerationEvent>(this.channel(jobId));
        const wsSub = ws$.subscribe({
            next: (msg) => subject.next(msg),
            error: (err: unknown) => {
                subject.error(err);
                this.subscribedJobs.delete(jobId);
            },
            complete: () => {
                subject.complete();
                this.subscribedJobs.delete(jobId);
            },
        });
        this.subscribedJobs.set(jobId, { wsSubscription: wsSub, subject });
        return subject.asObservable();
    }

    /**
     * Unsubscribes from an exercise-generation job channel.
     * @param jobId the job identifier
     */
    unsubscribeFromJob(jobId: string): void {
        const s = this.subscribedJobs.get(jobId);
        if (!s) {
            return;
        }
        s.wsSubscription.unsubscribe();
        s.subject.complete();
        this.subscribedJobs.delete(jobId);
    }

    ngOnDestroy(): void {
        this.subscribedJobs.forEach((s) => {
            s.wsSubscription.unsubscribe();
            s.subject.complete();
        });
        this.subscribedJobs.clear();
    }

    private channel(jobId: string): string {
        return `/user/topic/hyperion/exercise-generation/jobs/${jobId}`;
    }
}
