import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { CourseIngestionBrowserComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-browser/course-ingestion-browser.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { CourseBrowserData, IngestionCoverage } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

describe('CourseIngestionBrowserComponent', () => {
    let component: CourseIngestionBrowserComponent;
    let fixture: ComponentFixture<CourseIngestionBrowserComponent>;
    let service: CourseIngestionDashboardService;

    const course: IngestionCoverage = {
        courseId: 7,
        courseTitle: 'Introduction to Deep Learning',
        releaseDate: null,
        active: true,
        semester: 'WS26',
        status: 'INCOMPLETE',
        coverageGapScore: 3,
        computedAt: '2026-08-26T10:00:00Z',
        lastIngestedAt: '2026-08-26T09:00:00Z',
        typeCounts: [
            { type: 'exercise', expected: 4, indexed: 2, missing: 2, orphaned: 0 },
            { type: 'lecture', expected: 3, indexed: 3, missing: 0, orphaned: 0 },
            { type: 'faq', expected: 2, indexed: 1, missing: 0, orphaned: 1 },
        ],
    };

    const browserData: CourseBrowserData = {
        entities: [{ type: 'lecture', entityId: 20, title: 'Week 1', ingestedAt: '2026-08-26T09:00:00Z', properties: { title: 'Week 1' } }],
        contentPresence: [{ key: 'slides', unitIds: [10, 11] }],
        missingEntities: [{ type: 'exercise', entityId: 5, title: 'Sorting' }],
        contentGaps: [{ lectureUnitId: 11, title: 'Video unit', kind: 'transcript' }],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionBrowserComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);

        fixture = TestBed.createComponent(CourseIngestionBrowserComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should not load anything while it is closed', async () => {
        const spy = vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of(browserData));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should load the four datasets for the course when opened', async () => {
        const spy = vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of(browserData));

        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(spy).toHaveBeenCalledWith(course.courseId);
        expect(component.data()).toEqual(browserData);
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
    });

    it('should use the singular label when exactly one type is incomplete', () => {
        fixture.componentRef.setInput('course', { ...course, typeCounts: [{ type: 'exercise', expected: 4, indexed: 2, missing: 2, orphaned: 0 }] });

        expect(component.incompleteTypeCount()).toBe(1);
        expect(component.incompleteTypesLabelKey()).toContain('typeIncomplete');
    });

    it('should use the plural label for more than one incomplete type', () => {
        expect(component.incompleteTypesLabelKey()).toContain('typesIncomplete');
    });

    it('should count the types that are missing or orphaned for the header chip', () => {
        // Two exercises missing and one orphaned FAQ are two incomplete types; the fully indexed lecture type is not one.
        expect(component.incompleteTypeCount()).toBe(2);
    });

    it('should surface an error instead of a half-rendered browser when a load fails', async () => {
        vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(throwError(() => new Error('boom')));

        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.error()).toBe(true);
        expect(component.data()).toBeUndefined();
        expect(component.loading()).toBe(false);
    });

    it('should report an empty course when the index holds nothing for it', async () => {
        vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of({ entities: [], contentPresence: [], missingEntities: [], contentGaps: [] }));

        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isEmpty()).toBe(true);
    });

    it('should not report an empty course before anything has loaded', () => {
        expect(component.isEmpty()).toBe(false);
    });

    it('should drop the loaded data on close so reopening shows current state', async () => {
        vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of(browserData));
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();

        component.close();

        expect(component.visible()).toBe(false);
        expect(component.data()).toBeUndefined();
    });

    it('should render the master-detail shell once loaded', async () => {
        vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of(browserData));

        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(document.querySelector('[data-testid="browser-master-detail"]')).toBeTruthy();
        expect(document.querySelector('[data-testid="browser-navigation"]')).toBeTruthy();
        expect(document.querySelector('[data-testid="browser-detail"]')).toBeTruthy();
    });
});
