import { Injectable, OnDestroy, inject } from '@angular/core';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';

export type HyperionEvent =
    | { type: 'STARTED' | 'PROGRESS'; iteration?: number }
    | { type: 'FILE_UPDATED' | 'NEW_FILE'; path: string }
    | { type: 'DONE'; success: boolean; attempts: number; message?: string }
    | { type: 'ERROR'; message?: string };

type SubscribedJob = { wsSubscription: Subscription; subject: Subject<HyperionEvent> };

@Injectable({ providedIn: 'root' })
export class HyperionWebsocketService implements OnDestroy {
    protected websocketService = inject(WebsocketService);
    private subscribedJobs = new Map<string, SubscribedJob>();

    /**
     * Subscribes to a code generation job channel.
     * @param jobId job identifier
     * @returns observable stream of job events
     */
    subscribeToJob(jobId: string): Observable<HyperionEvent> {
        const existing = this.subscribedJobs.get(jobId);
        if (existing) {
            return existing.subject.asObservable();
        }
        const subject = new Subject<HyperionEvent>();
        const channel = this.channel(jobId);
        const wsSub = this.websocketService
            .subscribe(channel)
            .receive(channel)
            .subscribe({
                next: (msg: any) => {
                    subject.next(msg as HyperionEvent);
                },
                error: (err) => {
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
     * Unsubscribes from a code generation job channel.
     * @param jobId job identifier
     */
    unsubscribeFromJob(jobId: string): void {
        const s = this.subscribedJobs.get(jobId);
        if (!s) return;
        s.wsSubscription.unsubscribe();
        this.websocketService.unsubscribe(this.channel(jobId));
        s.subject.complete();
        this.subscribedJobs.delete(jobId);
    }

    /**
     * Cleans up all active job subscriptions.
     */
    ngOnDestroy(): void {
        this.subscribedJobs.forEach((s, jobId) => {
            s.wsSubscription.unsubscribe();
            this.websocketService.unsubscribe(this.channel(jobId));
            s.subject.complete();
        });
    }

    private channel(jobId: string) {
        return `/user/topic/hyperion/code-generation/jobs/${jobId}`;
    }
}
