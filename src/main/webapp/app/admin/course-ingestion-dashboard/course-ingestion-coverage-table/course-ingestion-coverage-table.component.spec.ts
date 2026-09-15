import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';

import { PageableResult } from 'app/foundation/pagination/pageable-table';
import { CourseIngestionCoverageTableComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-coverage-table/course-ingestion-coverage-table.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IngestionCoverage } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

describe('CourseIngestionCoverageTableComponent', () => {
    let component: CourseIngestionCoverageTableComponent;
    let fixture: ComponentFixture<CourseIngestionCoverageTableComponent>;
    let service: CourseIngestionDashboardService;
    let liveSpy: ReturnType<typeof vi.spyOn>;
    let storedSpy: ReturnType<typeof vi.spyOn>;
    let refreshSpy: ReturnType<typeof vi.spyOn>;

    // Row 1 exercises every cell branch: a gap (danger), a complete type (success), and a present-only/empty type (dash).
    // Row 2 has an empty title, no release date, and no type counts, so its cells all render as absent (dash).
    const rows: IngestionCoverage[] = [
        {
            courseId: 1,
            courseTitle: 'Algorithms',
            releaseDate: '2026-01-01T00:00:00Z',
            active: true,
            semester: 'WS26',
            status: 'INCOMPLETE',
            coverageGapScore: 7,
            computedAt: '2026-08-05T10:00:00Z',
            lastIngestedAt: '2026-08-04T09:00:00Z',
            typeCounts: [
                { type: 'exercise', expected: 10, indexed: 3, missing: 7, orphaned: 0 },
                { type: 'lecture', expected: 5, indexed: 5, missing: 0, orphaned: 0 },
                { type: 'faq', expected: 0, indexed: 0, missing: 0, orphaned: 0 },
            ],
        },
        {
            courseId: 2,
            courseTitle: '',
            releaseDate: null,
            active: false,
            semester: null,
            status: 'COMPLETE',
            coverageGapScore: 0,
            computedAt: '2026-08-05T10:00:00Z',
            lastIngestedAt: null,
            typeCounts: [],
        },
    ];

    const pageOf = (content: IngestionCoverage[]): Observable<PageableResult<IngestionCoverage>> => of({ content, totalElements: content.length });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionCoverageTableComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        liveSpy = vi.spyOn(service, 'getLiveCoveragePage').mockReturnValue(pageOf(rows));
        storedSpy = vi.spyOn(service, 'getStoredCoverage').mockReturnValue(pageOf(rows));
        refreshSpy = vi.spyOn(service, 'refreshCoverage').mockReturnValue(of(undefined));

        fixture = TestBed.createComponent(CourseIngestionCoverageTableComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('loads the live page on init and renders the matrix with formatted cells', async () => {
        fixture.detectChanges();

        // The default name sort is a per-course view, served live rather than from the stored projection.
        expect(liveSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'title,asc', search: undefined });
        expect(storedSpy).not.toHaveBeenCalled();
        expect(component.rows()).toEqual(rows);
        expect(component.totalRecords()).toBe(2);
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
        expect(component.lastUpdated()).toBe('2026-08-05T10:00:00Z');

        // Ascending column shows the up arrow, inactive columns the neutral one; EMPTY is the one status not in the rows.
        expect(component['sortIcon']('name')).toBe(faSortUp);
        expect(component['sortIcon']('release')).toBe(faSort);
        expect(component['statusClass']('EMPTY')).toBe('text-muted-color');

        await fixture.whenStable();
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        expect(element.querySelectorAll('table tbody tr')).toHaveLength(2);
        // Renders the gap cell (danger), the complete cell (success), the empty/absent cells (dash), the group dividers,
        // and the INCOMPLETE/COMPLETE status colours, exercising cell(), dividerAt() and statusClass() through bindings.
        expect(element.textContent).toContain('3/10');
        expect(element.textContent).toContain('5/5');
    });

    it('clears the rows and sets the error signal when the load fails', () => {
        liveSpy.mockReturnValue(throwError(() => new Error('boom')));
        fixture.detectChanges();

        expect(component.error()).toBe(true);
        expect(component.rows()).toEqual([]);
        expect(component.totalRecords()).toBe(0);
        expect(component.loading()).toBe(false);
    });

    it('sorts by column, paginates, and reads the stored projection for the worst-first sort', () => {
        fixture.detectChanges();
        liveSpy.mockClear();

        // Re-clicking the active column flips the direction and resets to the first page.
        component['toggleSort']('name');
        expect(component.sortDirection()).toBe('desc');
        expect(component['sortIcon']('name')).toBe(faSortDown);
        expect(liveSpy).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'title,desc', search: undefined });

        // Paging keeps the current (live) view and sort.
        component['onPageChange'](2);
        expect(component.page()).toBe(2);
        expect(liveSpy).toHaveBeenLastCalledWith({ page: 2, size: 20, sort: 'title,desc', search: undefined });

        // Switching to a new column adopts that column's default direction.
        component['toggleSort']('release');
        expect(component.sortMode()).toBe('release');
        expect(component.sortDirection()).toBe('desc');
        expect(component['sortIcon']('name')).toBe(faSort);
        expect(liveSpy).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'startDate,desc', search: undefined });

        // Worst-first is cross-course, so it is served from the stored projection.
        component['toggleSort']('worstFirst');
        expect(storedSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'coverageGapScore,desc', status: undefined, active: undefined });
    });

    it('filters by status and active state, and refreshes the projection', () => {
        fixture.detectChanges();

        // A status filter is a cross-course view, so it reads the stored projection.
        component['onStatusChange']('INCOMPLETE');
        expect(component.statusFilter()).toBe('INCOMPLETE');
        expect(storedSpy).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'courseTitle,asc', status: 'INCOMPLETE', active: undefined });

        // Clearing the status returns to the live per-page view.
        liveSpy.mockClear();
        component['onStatusChange']('');
        expect(component.statusFilter()).toBeUndefined();
        expect(liveSpy).toHaveBeenCalled();

        // The active filter maps to the string the select button binds to, and reads the stored projection.
        expect(component['activeFilterValue']()).toBe('');
        component['onActiveChange']('true');
        expect(component.activeFilter()).toBe(true);
        expect(component['activeFilterValue']()).toBe('true');
        expect(storedSpy).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'courseTitle,asc', status: undefined, active: true });
        component['onActiveChange']('false');
        expect(component['activeFilterValue']()).toBe('false');
        component['onActiveChange']('');
        expect(component.activeFilter()).toBeUndefined();

        // Refresh recomputes then reloads, clearing the flag on both success and failure.
        refreshSpy.mockClear();
        liveSpy.mockClear();
        component['onRefresh']();
        expect(refreshSpy).toHaveBeenCalled();
        expect(component.refreshing()).toBe(false);
        expect(liveSpy).toHaveBeenCalled();

        refreshSpy.mockReturnValue(throwError(() => new Error('nope')));
        component['onRefresh']();
        expect(component.refreshing()).toBe(false);
    });

    it('debounces the search input before reloading', () => {
        fixture.detectChanges();
        liveSpy.mockClear();
        vi.useFakeTimers();

        component['onSearchInput']('algo');
        expect(liveSpy).not.toHaveBeenCalled();

        vi.advanceTimersByTime(300);
        vi.useRealTimers();

        expect(component.search()).toBe('algo');
        expect(component.page()).toBe(0);
        expect(liveSpy).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'title,asc', search: 'algo' });
    });

    it('should rebuild the filter labels when the language changes', async () => {
        const translateService = TestBed.inject(TranslateService);
        vi.spyOn(translateService, 'instant').mockImplementation((key) => `de:${key}`);

        fixture.detectChanges();
        await fixture.whenStable();

        // Built once at construction, the labels would keep the fallback language for the life of the component.
        translateService.use('de');
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component['statusOptions']()[0].label).toBe('de:artemisApp.courseIngestionDashboard.matrix.status.all');
        expect(component['activeOptions']()[1].label).toBe('de:artemisApp.courseIngestionDashboard.matrix.active.active');
    });
});
