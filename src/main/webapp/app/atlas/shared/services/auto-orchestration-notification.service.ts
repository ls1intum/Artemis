import { Injectable, OnDestroy, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { WebsocketService } from 'app/shared/service/websocket.service';

/**
 * WebSocket payload broadcast by `ContentChangeScheduler` after an automatic
 * orchestrator run completes for a course. Mirrors `AutoOrchestrationSummaryDTO` on the server.
 */
export interface AutoOrchestrationSummary {
    courseId: number;
    runId: string;
    exerciseCount: number;
    successCount: number;
    failureCount: number;
    completedAt: string;
}

/**
 * Subscribes to `/topic/atlas/orchestrator/{courseId}` for the courses an instructor is currently
 * viewing and surfaces a toast notification when the auto-trigger pipeline finishes a run. The
 * subscription is short-lived: callers (the course dashboard component) call `subscribeToCourse`
 * on init and `unsubscribeFromCourse` on destroy so we never accumulate orphan subscriptions.
 */
@Injectable({ providedIn: 'root' })
export class AutoOrchestrationNotificationService implements OnDestroy {
    private readonly websocketService = inject(WebsocketService);
    private readonly alertService = inject(AlertService);
    private readonly subscriptions = new Map<number, Subscription>();

    subscribeToCourse(courseId: number): void {
        if (this.subscriptions.has(courseId)) {
            return;
        }
        const topic = `/topic/atlas/orchestrator/${courseId}`;
        const sub = this.websocketService.subscribe<AutoOrchestrationSummary>(topic).subscribe((summary) => this.handleSummary(summary));
        this.subscriptions.set(courseId, sub);
    }

    unsubscribeFromCourse(courseId: number): void {
        const sub = this.subscriptions.get(courseId);
        if (sub) {
            sub.unsubscribe();
            this.subscriptions.delete(courseId);
        }
    }

    ngOnDestroy(): void {
        this.subscriptions.forEach((sub) => sub.unsubscribe());
        this.subscriptions.clear();
    }

    private handleSummary(summary: AutoOrchestrationSummary): void {
        if (!summary) {
            return;
        }
        const params = { count: summary.exerciseCount, success: summary.successCount, failure: summary.failureCount };
        if (summary.failureCount === 0) {
            this.alertService.success('artemisApp.atlasOrchestrator.autoToast.success', params);
        } else if (summary.successCount === 0) {
            this.alertService.error('artemisApp.atlasOrchestrator.autoToast.failure', params);
        } else {
            this.alertService.warning('artemisApp.atlasOrchestrator.autoToast.partial', params);
        }
    }
}
