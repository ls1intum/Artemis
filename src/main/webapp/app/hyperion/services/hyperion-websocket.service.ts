import { Injectable, OnDestroy, inject } from '@angular/core';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

export type HyperionCompletionStatus = 'SUCCESS' | 'PARTIAL' | 'ERROR';
export type HyperionCompletionReason = 'BUILD_SUCCEEDED' | 'NO_COMMITTED_FILES' | 'BUILD_FAILED' | 'BUILD_TIMED_OUT' | 'PARTICIPATION_NOT_FOUND' | 'CI_TRIGGER_FAILED';

export type HyperionEvent =
    | { type: 'STARTED' | 'PROGRESS'; iteration?: number }
    | { type: 'FILE_UPDATED' | 'NEW_FILE' | 'FILE_DELETED'; path: string; iteration?: number }
    | {
          type: 'DONE';
          success: boolean;
          completionStatus?: HyperionCompletionStatus;
          completionReason?: HyperionCompletionReason;
          completionReasonParams?: Record<string, string>;
          attempts: number;
          message?: string;
      }
    | { type: 'ERROR'; message?: string };

type SubscribedJob = { wsSubscription: Subscription; subject: Subject<HyperionEvent> };

@Injectable({ providedIn: 'root' })
export class HyperionWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);
    private subscribedJobs = new Map<string, SubscribedJob>();

    subscribeToJob(jobId: string): Observable<HyperionEvent> {
        const existing = this.subscribedJobs.get(jobId);
        if (existing) {
            return existing.subject.asObservable();
        }
        const subject = new Subject<HyperionEvent>();
        const channel = this.channel(jobId);
        const ws$ = this.websocketService.subscribe<HyperionEvent>(channel);
        const wsSub = ws$.subscribe({
            next: (msg) => {
                subject.next(msg);
            },
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
        const s = this.subscribedJobs.get(jobId);
        if (!s) return;
        s.wsSubscription.unsubscribe();
        s.subject.complete();
        this.subscribedJobs.delete(jobId);
    }

    ngOnDestroy(): void {
        this.subscribedJobs.forEach((s) => {
            s.wsSubscription.unsubscribe();
            s.subject.complete();
        });
    }

    private channel(jobId: string) {
        return `/user/topic/hyperion/code-generation/jobs/${jobId}`;
    }
}
