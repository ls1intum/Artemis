import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CourseIngestionLogsComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-logs/course-ingestion-logs.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IngestionLogEntry } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

const entries: IngestionLogEntry[] = [
    {
        source: 'IRIS',
        occurredAt: '2026-09-22T22:11:02Z',
        level: 'ERROR',
        logger: 'iris.pipeline.lecture',
        message: 'slide vision failed',
        stackTrace: 'Traceback...\n  ValueError',
    },
    { source: 'ARTEMIS', occurredAt: '2026-09-22T22:11:01Z', level: 'DEBUG', logger: 'de.tum.cit.aet.artemis.lecture', message: 'dispatching unit 42', stackTrace: undefined },
];

describe('CourseIngestionLogsComponent', () => {
    let component: CourseIngestionLogsComponent;
    let fixture: ComponentFixture<CourseIngestionLogsComponent>;
    let service: CourseIngestionDashboardService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionLogsComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        vi.spyOn(service, 'getIngestionLogs').mockReturnValue(of(entries));
        fixture = TestBed.createComponent(CourseIngestionLogsComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.restoreAllMocks());

    it('should render records from both services in one stream', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="log-source-IRIS"]')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('[data-testid="log-source-ARTEMIS"]')).toBeTruthy();
        expect(component.visibleEntries()).toHaveLength(2);
    });

    it('should keep a stack trace collapsed until it is asked for', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="log-trace-0"]')).toBeNull();

        component['toggleStackTrace'](0);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="log-trace-0"]').textContent).toContain('ValueError');
    });

    it('should narrow to a level and clear the filter when the same level is chosen again', () => {
        fixture.detectChanges();

        component['selectLevel']('ERROR');
        expect(component.selectedLevel()).toBe('ERROR');
        expect(service.getIngestionLogs).toHaveBeenCalledWith('ERROR');

        component['selectLevel']('ERROR');
        expect(component.selectedLevel()).toBeUndefined();
    });

    it('should filter client-side by message and by logger', () => {
        fixture.detectChanges();

        component['onSearch']('vision');
        expect(component.visibleEntries()).toHaveLength(1);

        component['onSearch']('de.tum');
        expect(component.visibleEntries()).toHaveLength(1);
        expect(component.visibleEntries()[0].source).toBe('ARTEMIS');
    });

    it('should surface an error rather than an empty log when the request fails', () => {
        vi.spyOn(service, 'getIngestionLogs').mockReturnValue(throwError(() => new Error('boom')));

        fixture.detectChanges();

        expect(component.error()).toBe(true);
        expect(component.isEmpty()).toBe(false);
    });
});
