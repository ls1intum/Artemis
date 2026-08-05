import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';
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

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('loads the live coverage page on init with the default name sort', () => {
        fixture.detectChanges();

        expect(liveSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'title,asc', search: undefined });
        expect(storedSpy).not.toHaveBeenCalled();
        expect(component.rows()).toEqual(rows);
        expect(component.totalRecords()).toBe(2);
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
        expect(component.lastUpdated()).toBe('2026-08-05T10:00:00Z');
    });

    it('sets the error signal and clears rows when the load fails', () => {
        liveSpy.mockReturnValue(throwError(() => new Error('boom')));
        fixture.detectChanges();

        expect(component.error()).toBe(true);
        expect(component.rows()).toEqual([]);
        expect(component.totalRecords()).toBe(0);
        expect(component.loading()).toBe(false);
    });

    it('toggles the sort direction when the active column is clicked again', () => {
        fixture.detectChanges();
        liveSpy.mockClear();

        component['toggleSort']('name');

        expect(component.sortDirection()).toBe('desc');
        expect(component.page()).toBe(0);
        expect(liveSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'title,desc', search: undefined });
    });

    it('switches to a new column with its default direction', () => {
        fixture.detectChanges();
        liveSpy.mockClear();

        component['toggleSort']('release');

        expect(component.sortMode()).toBe('release');
        expect(component.sortDirection()).toBe('desc');
        expect(liveSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'startDate,desc', search: undefined });
    });

    it('reads the stored projection for the cross-course worst-first sort', () => {
        fixture.detectChanges();

        component['toggleSort']('worstFirst');

        expect(storedSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'coverageGapScore,desc', status: undefined, active: undefined });
    });

    it('reports the sort icon for the active and inactive columns', () => {
        fixture.detectChanges();

        expect(component['sortIcon']('name')).toBe(faSortUp);
        expect(component['sortIcon']('release')).toBe(faSort);

        component['toggleSort']('name');
        expect(component['sortIcon']('name')).toBe(faSortDown);
    });

    it('filters by status through the stored projection and back to the live view', () => {
        fixture.detectChanges();

        component['onStatusChange']('INCOMPLETE');
        expect(component.statusFilter()).toBe('INCOMPLETE');
        expect(storedSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'courseTitle,asc', status: 'INCOMPLETE', active: undefined });

        liveSpy.mockClear();
        component['onStatusChange']('');
        expect(component.statusFilter()).toBeUndefined();
        expect(liveSpy).toHaveBeenCalled();
    });

    it('filters by the active flag and exposes it as the select-button value', () => {
        fixture.detectChanges();

        expect(component['activeFilterValue']()).toBe('');

        component['onActiveChange']('true');
        expect(component.activeFilter()).toBe(true);
        expect(component['activeFilterValue']()).toBe('true');
        expect(storedSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'courseTitle,asc', status: undefined, active: true });

        component['onActiveChange']('false');
        expect(component.activeFilter()).toBe(false);
        expect(component['activeFilterValue']()).toBe('false');

        component['onActiveChange']('');
        expect(component.activeFilter()).toBeUndefined();
    });

    it('loads the requested page on a page change', () => {
        fixture.detectChanges();
        liveSpy.mockClear();

        component['onPageChange'](2);

        expect(component.page()).toBe(2);
        expect(liveSpy).toHaveBeenCalledWith({ page: 2, size: 20, sort: 'title,asc', search: undefined });
    });

    it('refreshes the projection then reloads, and clears the flag on success', () => {
        fixture.detectChanges();
        liveSpy.mockClear();

        component['onRefresh']();

        expect(refreshSpy).toHaveBeenCalled();
        expect(component.refreshing()).toBe(false);
        expect(liveSpy).toHaveBeenCalled();
    });

    it('clears the refreshing flag when the refresh fails', () => {
        fixture.detectChanges();
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
        expect(liveSpy).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'title,asc', search: 'algo' });
    });

    it('formats a coverage cell as indexed/expected with the right severity colour', () => {
        const missing = component['cell'](rows[0], 'exercise');
        expect(missing.text).toBe('3/10');
        expect(missing.class).toContain('text-state-danger');

        const complete = component['cell'](rows[0], 'lecture');
        expect(complete.text).toBe('5/5');
        expect(complete.class).toContain('text-state-success');
    });

    it('renders a dash for a type that is neither expected nor indexed, or absent from the row', () => {
        expect(component['cell'](rows[0], 'faq').text).toBe('–');
        expect(component['cell'](rows[1], 'exercise').text).toBe('–');
        expect(component['cell'](rows[1], 'exercise').class).toContain('text-muted-color');
    });

    it('draws the group divider before the first metadata and first content column only', () => {
        expect(component['dividerAt'](0)).not.toBe('');
        expect(component['dividerAt'](7)).not.toBe('');
        expect(component['dividerAt'](1)).toBe('');
    });

    it('maps each coverage status to its semantic colour', () => {
        expect(component['statusClass']('COMPLETE')).toBe('text-state-success');
        expect(component['statusClass']('INCOMPLETE')).toBe('text-state-danger');
        expect(component['statusClass']('EMPTY')).toBe('text-muted-color');
    });

    it('renders one table row per course with formatted cells', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        const bodyRows = element.querySelectorAll('table tbody tr');
        expect(bodyRows).toHaveLength(2);
        expect(element.textContent).toContain('3/10');
    });
});
