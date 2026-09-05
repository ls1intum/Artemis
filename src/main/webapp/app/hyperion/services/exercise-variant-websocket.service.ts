import { Injectable, OnDestroy, inject } from '@angular/core';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

export type VariantJobPhase =
    'ANALYZING' | 'PLANNING' | 'PROVISIONING' | 'TRANSFORMING' | 'VERIFYING' | 'REPAIRING' | 'FINALIZING' | 'COMPLETED' | 'DRAFT_WITH_WARNINGS' | 'FAILED' | 'CANCELLED';

/** Mirror of the server's VariantGenerationEventDTO. */
export interface VariantGenerationEvent {
    type: 'PHASE_CHANGED' | 'PROGRESS' | 'ATTEMPT' | 'STEP_OUTPUT' | 'DONE' | 'FAILED' | 'CANCELLED';
    phase?: VariantJobPhase;
    attempt?: number;
    maxAttempts?: number;
    detail?: string;
    variantExerciseId?: number;
    warnings?: string[];
}

export const TERMINAL_VARIANT_PHASES: readonly VariantJobPhase[] = ['COMPLETED', 'DRAFT_WITH_WARNINGS', 'FAILED', 'CANCELLED'];

export function isTerminalVariantPhase(phase: VariantJobPhase | undefined): boolean {
    return phase != undefined && TERMINAL_VARIANT_PHASES.includes(phase);
}

type SubscribedJob = { wsSubscription: Subscription; subject: Subject<VariantGenerationEvent> };

/**
 * Per-job websocket subscriptions for variant-generation progress on
 * `/user/topic/hyperion/variant-generation/jobs/{jobId}`. Same multiplexing pattern as
 * `HyperionWebsocketService.subscribeToJob`, kept separate because that service is bound to the
 * code-generation topic and is owned by another team.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseVariantWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);
    private subscribedJobs = new Map<string, SubscribedJob>();

    subscribeToJob(jobId: string): Observable<VariantGenerationEvent> {
        const existing = this.subscribedJobs.get(jobId);
        if (existing) {
            return existing.subject.asObservable();
        }
        const subject = new Subject<VariantGenerationEvent>();
        const ws$ = this.websocketService.subscribe<VariantGenerationEvent>(this.channel(jobId));
        const wsSub = ws$.subscribe({
            next: (event) => subject.next(event),
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

    unsubscribeFromJob(jobId: string): void {
        const subscribed = this.subscribedJobs.get(jobId);
        if (!subscribed) return;
        subscribed.wsSubscription.unsubscribe();
        subscribed.subject.complete();
        this.subscribedJobs.delete(jobId);
    }

    ngOnDestroy(): void {
        this.subscribedJobs.forEach((subscribed) => {
            subscribed.wsSubscription.unsubscribe();
            subscribed.subject.complete();
        });
        this.subscribedJobs.clear();
    }

    private channel(jobId: string) {
        return `/user/topic/hyperion/variant-generation/jobs/${jobId}`;
    }
}
