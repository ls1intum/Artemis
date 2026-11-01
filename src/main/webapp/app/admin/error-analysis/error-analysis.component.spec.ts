import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Subject, of, throwError } from 'rxjs';

import { ErrorAnalysisComponent } from './error-analysis.component';
import { ErrorAnalysisService } from './error-analysis.service';
import { ErrorGroup } from './error-analysis.model';
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

describe('ErrorAnalysisComponent', () => {
    let fixture: ComponentFixture<ErrorAnalysisComponent>;
    let component: ErrorAnalysisComponent;
    let getGroups: ReturnType<typeof vi.fn>;
    let alertError: ReturnType<typeof vi.fn>;

    const create = async () => {
        fixture = TestBed.createComponent(ErrorAnalysisComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
        fixture.detectChanges();
    };

    const rows = () => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="error-analysis-row"]')) as HTMLElement[];

    beforeEach(() => {
        getGroups = vi.fn().mockReturnValue(of([group()]));
        alertError = vi.fn();
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ErrorAnalysisService, useValue: { getGroups } },
                { provide: AlertService, useValue: { error: alertError } },
            ],
        });
    });

    it('should show the errors of the server and the clients of the last day that are errors by default', async () => {
        await create();

        expect(getGroups).toHaveBeenCalledExactlyOnceWith('DAY', 'ALL', 'ERROR');
        expect(component.span()).toBe('DAY');
        expect(component.location()).toBe('ALL');
        expect(component.minSeverity()).toBe('ERROR');
    });

    it('should show a row with the severity, location, message, count and when the error happened first and last', async () => {
        getGroups.mockReturnValue(of([group({ location: 'CLIENT', severity: 'CRITICAL', message: 'Cannot start', count: 12 })]));

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

    it('should total the errors of all groups', async () => {
        getGroups.mockReturnValue(of([group({ count: 4 }), group({ message: 'another', count: 6 })]));

        await create();

        expect(component.totalCount()).toBe(10);
        expect(rows()).toHaveLength(2);
    });

    it('should say so if there are no errors', async () => {
        getGroups.mockReturnValue(of([]));

        await create();

        expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-empty"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-table"]')).toBeNull();
    });

    it('should always say that the analysis starts again when the node restarts', async () => {
        getGroups.mockReturnValue(of([]));

        await create();

        expect(fixture.nativeElement.querySelector('[data-testid="error-analysis-note"]')).not.toBeNull();
    });

    it.each([
        ['span', 'MONTH', ['MONTH', 'ALL', 'ERROR']],
        ['location', 'CLIENT', ['DAY', 'CLIENT', 'ERROR']],
        ['minSeverity', 'WARN', ['DAY', 'ALL', 'WARN']],
    ] as const)('should load again when the %s filter changes', async (filter, value, expectedArguments) => {
        await create();
        getGroups.mockClear();

        (component[filter] as unknown as { set(value: string): void }).set(value);
        await fixture.whenStable();

        expect(getGroups).toHaveBeenCalledExactlyOnceWith(...expectedArguments);
    });

    it('should tell the user if the analysis cannot be loaded', async () => {
        getGroups.mockReturnValue(throwError(() => new Error('boom')));

        await create();

        expect(alertError).toHaveBeenCalledExactlyOnceWith('artemisApp.errorAnalysis.loadError');
        expect(component.loading()).toBe(false);
    });

    it('should show the answer of the last filter even if an earlier request answers later', async () => {
        const first = new Subject<ErrorGroup[]>();
        const second = new Subject<ErrorGroup[]>();
        getGroups.mockReturnValueOnce(first).mockReturnValueOnce(second);
        await create();

        component.span.set('WEEK');
        await fixture.whenStable();
        second.next([group({ message: 'answer to the week' })]);
        first.next([group({ message: 'late answer to the day' })]);
        fixture.detectChanges();

        expect(component.groups()?.map((g) => g.message)).toEqual(['answer to the week']);
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
