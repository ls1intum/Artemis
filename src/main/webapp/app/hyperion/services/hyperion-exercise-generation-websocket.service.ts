import { Injectable, OnDestroy, inject } from '@angular/core';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';
import { ExerciseGenerationEvent as GeneratedExerciseGenerationEvent } from 'app/openapi/model/exerciseGenerationEvent';

// Reuse the generated enums so the unions cannot drift from the server (the drift that broke the build).
export type ExerciseGenerationEventType = GeneratedExerciseGenerationEvent.TypeEnum;
export type ExerciseGenerationCompletionStatus = GeneratedExerciseGenerationEvent.CompletionStatusEnum;
export type { ExerciseGenerationVerdict } from 'app/openapi/model/exerciseGenerationVerdict';

/** A progress event streamed while an agentic exercise generation/adaptation runs (the generated OpenAPI model, including the structured verdict). */
export type ExerciseGenerationEvent = GeneratedExerciseGenerationEvent;

type SubscribedJob = { wsSubscription: Subscription; subject: Subject<ExerciseGenerationEvent> };

/** Subscribes to the websocket channel for an agentic exercise-generation job and exposes its progress as an observable stream. */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseGenerationWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);
    private subscribedJobs = new Map<string, SubscribedJob>();

    /** Subscribes to a job channel, returning a shared stream of its events (idempotent per {@code jobId}). */
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

    /** Unsubscribes from a job channel and tears down its stream. */
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
