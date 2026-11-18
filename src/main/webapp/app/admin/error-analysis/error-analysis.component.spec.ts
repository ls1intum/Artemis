import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Subject, of, throwError } from 'rxjs';

import { ErrorAnalysisComponent } from './error-analysis.component';
import { ErrorAnalysisService } from './error-analysis.service';
import { ErrorAnalysisPage, ErrorAnalysisQuery, ErrorGroup } from './error-analysis.model';
import { AlertService } from 'app/foundation/service/alert.service';

function group(overrides: Partial<ErrorGroup> = {}): ErrorGroup {
    return {
        location: 'SERVER',
        severity: 'ERROR',
        message: 'ExerciseService: Could not find exercise <n>',
        count: 4,
        firstSeen: '2026-11-01T10:00:00Z',
        lastSeen: '2026-11-01T12:00:00Z',
        ...overrides,
    };
}

function page(groups: ErrorGroup[], totalGroups = groups.length, totalErrors = groups.reduce((sum, g) => sum + g.count, 0)): ErrorAnalysisPage {
    return { groups, totalGroups, totalErrors };
}

describe('ErrorAnalysisComponent', () => {
    let fixture: ComponentFixture<ErrorAnalysisComponent>;
    let component: ErrorAnalysisComponent;
    let getGroups: ReturnType<typeof vi.fn<(query: ErrorAnalysisQuery) => unknown>>;
    let alertError: ReturnType<typeof vi.fn>;

    const create = async () => {
        fixture = TestBed.createComponent(ErrorAnalysisComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
        fixture.detectChanges();
    };

    const rows = () => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="error-analysis-row"]')) as HTMLElement[];
    const lastQuery = () => getGroups.mock.calls[getGroups.mock.calls.length - 1][0];

    beforeEach(() => {
        getGroups = vi.fn().mockReturnValue(of(page([group()])));
        alertError = vi.fn();
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ErrorAnalysisService, useValue: { getGroups } },
                { provide: AlertService, useValue: { error: alertError } },
            ],
        });
    });

    describe('what is shown by default', () => {
        it('should show the errors of the release that is running, the most frequent first, on the first page', async () => {
            await create();

            expect(getGroups).toHaveBeenCalledExactlyOnceWith({
                span: 'RELEASE',
                location: 'ALL',
                minSeverity: 'ERROR',
                sortField: 'COUNT',
                ascending: false,
                page: 0,
                pageSize: 25,
            });
        });

        it('should offer the release before the time spans', async () => {
            await create();

            expect(component['spanOptions'].map((option) => option.value)).toEqual(['RELEASE', 'DAY', 'WEEK', 'MONTH']);
        });

        it('should keep the note short and free of details of the infrastructure', async () => {
            await create();

            expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-note"]')).not.toBeNull();
        });
    });

    describe('rows', () => {
        it('should show a row with the severity, location, message, count and when the error happened first and last', async () => {
            getGroups.mockReturnValue(of(page([group({ location: 'CLIENT', severity: 'CRITICAL', message: 'Cannot start', count: 12 })])));

            await create();

            expect(rows()).toHaveLength(1);
            const row = rows()[0];
            expect(row.querySelector('[data-testid="error-severity"]')?.textContent).toContain('artemisApp.errorAnalysis.severity.CRITICAL');
            expect(row.querySelector('[data-testid="error-location"]')?.textContent).toContain('artemisApp.errorAnalysis.location.CLIENT');
            expect(row.querySelector('[data-testid="error-message"]')?.textContent).toContain('Cannot start');
            expect(row.querySelector('[data-testid="error-count"]')?.textContent?.trim()).toBe('12');
            expect(row.querySelector('[data-testid="error-first-seen"]')?.textContent?.trim()).not.toBe('');
            expect(row.querySelector('[data-testid="error-last-seen"]')?.textContent?.trim()).not.toBe('');
        });

        it('should show the totals of all pages and not only of the page', async () => {
            getGroups.mockReturnValue(of(page([group()], 61, 480)));

            await create();

            expect(component.totalGroups()).toBe(61);
            expect(component.totalErrors()).toBe(480);
            expect(rows()).toHaveLength(1);
        });

        it('should say so if there are no errors, and show no table and no pages', async () => {
            getGroups.mockReturnValue(of(page([])));

            await create();

            expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-empty"]')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-table"]')).toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-paginator"]')).toBeNull();
        });

        it('should show the pages of a table that has rows', async () => {
            await create();

            expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-paginator"]')).not.toBeNull();
        });
    });

    describe('filters', () => {
        it.each([
            ['span', 'MONTH', { span: 'MONTH' }],
            ['location', 'CLIENT', { location: 'CLIENT' }],
            ['minSeverity', 'WARN', { minSeverity: 'WARN' }],
        ] as const)('should load again when the %s filter changes', async (filter, value, expectedChange) => {
            await create();
            getGroups.mockClear();

            (component[filter] as unknown as { set(value: string): void }).set(value);
            await fixture.whenStable();

            expect(getGroups).toHaveBeenCalledExactlyOnceWith(expect.objectContaining(expectedChange));
        });

        it.each([
            ['setSpan', 'DAY'],
            ['setLocation', 'SERVER'],
            ['setMinSeverity', 'CRITICAL'],
        ] as const)('should start again at the first page when %s changes the filter', async (setter, value) => {
            await create();
            component['onPageChange'](3);
            await fixture.whenStable();

            (component[setter] as (value: string) => void)(value);
            await fixture.whenStable();

            expect(lastQuery().page).toBe(0);
        });
    });

    describe('sorting', () => {
        // many groups, so that every page exists
        beforeEach(() => getGroups.mockReturnValue(of(page([group()], 500, 2000))));

        it('should sort by the column that was chosen and take the direction of the table', async () => {
            await create();
            getGroups.mockClear();

            component['onSort']({ field: 'LAST_SEEN', order: 1 });
            await fixture.whenStable();

            expect(getGroups).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ sortField: 'LAST_SEEN', ascending: true }));
        });

        it('should sort descending when the table says so', async () => {
            await create();

            component['onSort']({ field: 'MESSAGE', order: -1 });
            await fixture.whenStable();

            expect(lastQuery()).toEqual(expect.objectContaining({ sortField: 'MESSAGE', ascending: false }));
        });

        it('should start again at the first page, because the page belongs to the sort order', async () => {
            await create();
            component['onPageChange'](4);
            await fixture.whenStable();
            expect(lastQuery().page).toBe(4);

            component['onSort']({ field: 'COUNT', order: 1 });
            await fixture.whenStable();

            expect(lastQuery()).toEqual(expect.objectContaining({ sortField: 'COUNT', ascending: true, page: 0 }));
        });

        it('should make every column sortable by the field that the server knows', async () => {
            await create();

            const headers = Array.from(fixture.nativeElement.querySelectorAll('thead th[data-testid^="sort-"]')) as HTMLElement[];
            expect(headers.map((header) => header.getAttribute('data-testid'))).toEqual([
                'sort-severity',
                'sort-location',
                'sort-message',
                'sort-count',
                'sort-first-seen',
                'sort-last-seen',
            ]);
        });
    });

    describe('paging', () => {
        // many groups, so that every page exists
        beforeEach(() => getGroups.mockReturnValue(of(page([group()], 500, 2000))));

        it('should ask for the page that was chosen and keep the sort order', async () => {
            await create();
            component['onSort']({ field: 'MESSAGE', order: 1 });
            await fixture.whenStable();
            getGroups.mockClear();

            component['onPageChange'](2);
            await fixture.whenStable();

            expect(getGroups).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ page: 2, sortField: 'MESSAGE', ascending: true }));
        });

        it('should start again at the first page when the size of a page changes', async () => {
            await create();
            component['onPageChange'](3);
            await fixture.whenStable();

            component['onPageSizeChange'](50);
            await fixture.whenStable();

            expect(lastQuery()).toEqual(expect.objectContaining({ page: 0, pageSize: 50 }));
        });

        it('should offer sizes that the server accepts', async () => {
            await create();

            expect(Math.max(...component['pageSizeOptions'])).toBeLessThanOrEqual(100);
            expect(component['pageSizeOptions']).toContain(25);
        });

        it('should go to the last page that exists when the page that was shown is gone', async () => {
            await create();
            getGroups.mockReturnValueOnce(of(page([], 30, 120))).mockReturnValue(of(page([group()], 30, 120)));

            component['onPageChange'](5);
            await fixture.whenStable();

            // 30 groups in pages of 25: the last page is page 1
            expect(component.page()).toBe(1);
            expect(rows()).toHaveLength(1);
        });
    });

    describe('loading', () => {
        it('should tell the user if the analysis cannot be loaded', async () => {
            getGroups.mockReturnValue(throwError(() => new Error('boom')));

            await create();

            expect(alertError).toHaveBeenCalledExactlyOnceWith('artemisApp.errorAnalysis.loadError');
            expect(component.loading()).toBe(false);
        });

        it('should show the answer of the last query even if an earlier request answers later', async () => {
            const first = new Subject<ErrorAnalysisPage>();
            const second = new Subject<ErrorAnalysisPage>();
            getGroups.mockReturnValueOnce(first).mockReturnValueOnce(second);
            await create();

            component['setSpan']('WEEK');
            await fixture.whenStable();
            second.next(page([group({ message: 'answer to the week' })]));
            first.next(page([group({ message: 'late answer to the release' })]));
            fixture.detectChanges();

            expect(component.groups().map((g) => g.message)).toEqual(['answer to the week']);
        });
    });

    it.each([
        ['WARN', 'warn'],
        ['ERROR', 'danger'],
        ['CRITICAL', 'danger'],
    ] as const)('should show a %s as a %s tag', async (severity, tag) => {
        await create();

        expect(component['severityTag'](severity)).toBe(tag);
    });
});
