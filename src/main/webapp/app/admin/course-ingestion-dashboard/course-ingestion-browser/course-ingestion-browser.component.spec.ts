import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
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
        entities: [{ type: 'lecture', entityId: 20, title: 'Week 1', ingestedAt: '2026-08-26T09:00:00Z' }],
        contentPresence: [{ key: 'slides', unitIds: [10, 11] }],
        missingEntities: [{ type: 'exercise', entityId: 5, title: 'Sorting' }],
        contentGaps: [{ lectureUnitId: 11, title: 'Video unit', kind: 'transcript' }],
        // Deliberately disagrees with the matrix row above: the row calls lecture complete, the live read does not.
        typeCounts: [
            { type: 'exercise', expected: 4, indexed: 2, missing: 2, orphaned: 0 },
            { type: 'lecture', expected: 3, indexed: 2, missing: 1, orphaned: 0 },
            { type: 'faq', expected: 2, indexed: 1, missing: 0, orphaned: 1 },
        ],
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

    it('should not refetch when the same course arrives as a new object', async () => {
        const spy = vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of(browserData));
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(spy).toHaveBeenCalledTimes(1);

        // The matrix hands over a fresh object whenever it reloads its rows; the course on screen has not changed.
        fixture.componentRef.setInput('course', { ...course });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(spy).toHaveBeenCalledTimes(1);
    });

    it('should fetch again when a different course is opened', async () => {
        const spy = vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of(browserData));
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.componentRef.setInput('course', { ...course, courseId: 8 });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(spy).toHaveBeenLastCalledWith(8);
        expect(spy).toHaveBeenCalledTimes(2);
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
        vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of({ entities: [], contentPresence: [], missingEntities: [], contentGaps: [], typeCounts: [] }));

        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isEmpty()).toBe(true);
    });

    it('should not report an empty course before anything has loaded', () => {
        expect(component.isEmpty()).toBe(false);
    });

    it('should still open the browser for a course that is only missing things', async () => {
        // Nothing indexed at all, so every gap is a missing entity. This is the most broken a course can be and the
        // one an admin most needs to look inside, which reporting it as empty would prevent entirely.
        vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(
            of({ entities: [], contentPresence: [], missingEntities: [{ type: 'course', entityId: 7, title: 'Dash Test A' }], contentGaps: [], typeCounts: [] }),
        );

        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isEmpty()).toBe(false);
        expect(document.querySelector('[data-testid="browser-master-detail"]')).toBeTruthy();
        expect(document.querySelector('[data-testid="browser-empty"]')).toBeFalsy();
    });

    it('should take its counts from the loaded payload rather than the matrix row', async () => {
        // A filtered or worst-first matrix row comes from the stored projection, so it can be as old as the last
        // recompute. Pairing it with the live gap lists let the scoreboard call a type complete while the pane beside
        // it named what was missing.
        vi.spyOn(service, 'getCourseBrowserData').mockReturnValue(of(browserData));
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.typeCounts()).toEqual(browserData.typeCounts);
        expect(component.typeCounts().find((count) => count.type === 'lecture')?.missing).toBe(1);
        // The chip counts the live incompletes (exercise, lecture, faq), not the row's two.
        expect(component.incompleteTypeCount()).toBe(3);
    });

    it('should fall back to the matrix row until the live counts arrive', () => {
        expect(component.typeCounts()).toEqual(course.typeCounts);
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

    it('should not let a superseded course load overwrite the course now on screen', async () => {
        const forFirstCourse = new Subject<CourseBrowserData>();
        const forSecondCourse = new Subject<CourseBrowserData>();
        const spy = vi.spyOn(service, 'getCourseBrowserData');
        spy.mockReturnValueOnce(forFirstCourse.asObservable()).mockReturnValueOnce(forSecondCourse.asObservable());

        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();

        // The administrator opens one course and switches to another before the first read comes back.
        fixture.componentRef.setInput('course', { ...course, courseId: 8 });
        fixture.detectChanges();
        await fixture.whenStable();

        const newer: CourseBrowserData = { ...browserData, entities: [{ type: 'lecture', entityId: 1, title: 'Course 8 lecture' }] };
        const stale: CourseBrowserData = { ...browserData, entities: [{ type: 'lecture', entityId: 2, title: 'Course 7 lecture' }] };
        forSecondCourse.next(newer);
        forFirstCourse.next(stale);
        fixture.detectChanges();

        // Without cancellation the stale read wins, and it also rewinds loadedCourseId to the course no longer shown,
        // so the modal displays one course's content under another course's heading.
        expect(component.data()?.entities[0].title).toBe('Course 8 lecture');
        expect(component['loadedCourseId']).toBe(8);
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
