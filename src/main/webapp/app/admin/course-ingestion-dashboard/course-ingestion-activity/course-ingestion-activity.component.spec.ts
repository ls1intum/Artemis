import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CourseIngestionActivityComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-activity/course-ingestion-activity.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IngestionActivity } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

const activity: IngestionActivity = {
    events: [
        { id: 2, kind: 'DRIFT_DETECTED', occurredAt: '2026-09-22T12:04:31Z', entityType: 'Exercise', entityId: 881, detail: 'queued a rewrite' },
        { id: 1, kind: 'INGESTED', occurredAt: '2026-09-22T12:03:58Z', entityType: 'LectureUnit', entityId: 40, courseId: 7, detail: 'completed after 0 retries' },
    ],
    countsByKind: { DRIFT_DETECTED: 1, INGESTED: 4, CLAIMED: 0 },
    summaryWindowHours: 24,
};

describe('CourseIngestionActivityComponent', () => {
    let component: CourseIngestionActivityComponent;
    let fixture: ComponentFixture<CourseIngestionActivityComponent>;
    let service: CourseIngestionDashboardService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionActivityComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        vi.spyOn(service, 'getActivity').mockReturnValue(of(activity));
        fixture = TestBed.createComponent(CourseIngestionActivityComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.restoreAllMocks());

    it('should render one row per event, newest first', () => {
        fixture.detectChanges();

        expect(component.events()).toHaveLength(2);
        expect(fixture.nativeElement.querySelector('[data-testid="activity-row-2"]')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('[data-testid="activity-row-1"]')).toBeTruthy();
    });

    it('should show a summary tile only for kinds that actually occurred', () => {
        fixture.detectChanges();

        // CLAIMED counted zero, so it earns no tile even though the server reported the key.
        expect(component.summary().map((tile) => tile.kind)).toEqual(['INGESTED', 'DRIFT_DETECTED']);
    });

    it('should narrow the feed to a kind and clear the filter when the same kind is chosen again', () => {
        fixture.detectChanges();

        component['selectKind']('INGESTED');
        expect(component.selectedKind()).toBe('INGESTED');
        expect(service.getActivity).toHaveBeenCalledWith({ kind: 'INGESTED' });

        component['selectKind']('INGESTED');
        expect(component.selectedKind()).toBeUndefined();
        expect(service.getActivity).toHaveBeenLastCalledWith({ kind: undefined });
    });

    it('should surface an error instead of an empty feed when the request fails', () => {
        vi.spyOn(service, 'getActivity').mockReturnValue(throwError(() => new Error('boom')));

        fixture.detectChanges();

        expect(component.error()).toBe(true);
        expect(component.loading()).toBe(false);
        expect(component.isEmpty()).toBe(false);
    });
});
