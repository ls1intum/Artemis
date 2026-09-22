import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CourseIngestionQueuesComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-queues/course-ingestion-queues.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { QueueOverview, RunningIngestion } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

const runningNow: RunningIngestion = {
    lectureUnitId: 42,
    lectureUnitName: 'Backpropagation',
    courseId: 7,
    courseTitle: 'Advanced Algorithms',
    phase: 'INGESTING',
    stage: 'vision',
    stageProgress: 3,
    stageTotal: 18,
    lastProgressAt: new Date().toISOString(),
    lastHeartbeatAt: new Date().toISOString(),
    lockedBy: 'boot-7a',
};

const overview: QueueOverview = {
    lectureIngestion: {
        countsByPhase: { IDLE: 5, INGESTING: 1, DONE: 120 },
        running: [runningNow],
        nextUp: [{ lectureUnitId: 43, lectureUnitName: 'Gradient descent', courseId: 7, courseTitle: 'Advanced Algorithms', dispatchPriority: 0 }],
        retryWaiting: 2,
    },
    weaviateOutbox: { total: 3, dueNow: 1, countsByOrigin: { LIVE: 2, RECONCILE_DRIFT: 1 }, maxAttempts: 0, head: [] },
    reconcilePasses: [{ pass: 'DRIFT', lastRunAt: '2026-09-22T12:00:00Z', entitiesChecked: 40, repairsEnqueued: 1 }],
    workers: [{ bootId: 'boot-7a', activeRuns: 1, lastHeartbeatAt: new Date().toISOString() }],
};

describe('CourseIngestionQueuesComponent', () => {
    let component: CourseIngestionQueuesComponent;
    let fixture: ComponentFixture<CourseIngestionQueuesComponent>;
    let service: CourseIngestionDashboardService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionQueuesComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        vi.spyOn(service, 'getQueues').mockReturnValue(of(overview));
        fixture = TestBed.createComponent(CourseIngestionQueuesComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.restoreAllMocks());

    it('should render the running unit and the queue head', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="queues-running-42"]')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('[data-testid="queues-nextup-43"]')).toBeTruthy();
    });

    it('should show a depth tile only for phases that hold something', () => {
        fixture.detectChanges();

        expect(component.phaseTiles().map((tile) => tile.phase)).toEqual(['IDLE', 'INGESTING', 'DONE']);
    });

    it('should render the stage with its progress counter', () => {
        fixture.detectChanges();

        expect(component['stageLabel'](runningNow)).toBe('vision 3/18');
    });

    it('should flag a run whose progress is frozen while its worker still heartbeats', () => {
        const wedged: RunningIngestion = { ...runningNow, lastProgressAt: new Date(Date.now() - 60 * 60 * 1000).toISOString() };

        expect(component['isWedged'](runningNow)).toBe(false);
        expect(component['isWedged'](wedged)).toBe(true);
    });

    it('should report an idle system rather than four empty tables', () => {
        vi.spyOn(service, 'getQueues').mockReturnValue(
            of({ lectureIngestion: { countsByPhase: {}, running: [], nextUp: [] }, weaviateOutbox: { countsByOrigin: {}, head: [] }, reconcilePasses: [], workers: [] }),
        );

        fixture.detectChanges();

        expect(component.isIdle()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="queues-idle"]')).toBeTruthy();
    });

    it('should surface an error when the snapshot cannot be read', () => {
        vi.spyOn(service, 'getQueues').mockReturnValue(throwError(() => new Error('boom')));

        fixture.detectChanges();

        expect(component.error()).toBe(true);
        expect(component.loading()).toBe(false);
    });
});
