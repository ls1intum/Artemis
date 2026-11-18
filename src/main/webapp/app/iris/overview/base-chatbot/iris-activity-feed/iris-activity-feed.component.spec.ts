import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { By } from '@angular/platform-browser';
import { IrisActivityFeedComponent, prettifyActivityName } from 'app/iris/overview/base-chatbot/iris-activity-feed/iris-activity-feed.component';
import { IrisActivityItem, IrisActivityKind, IrisActivityState } from 'app/iris/shared/entities/iris-activity.model';

describe('IrisActivityFeedComponent', () => {
    let fixture: ComponentFixture<IrisActivityFeedComponent>;
    let translateService: {
        instant: ReturnType<typeof vi.fn>;
        getCurrentLang: ReturnType<typeof vi.fn>;
        onTranslationChange: Subject<unknown>;
        onLangChange: Subject<unknown>;
        onDefaultLangChange: Subject<unknown>;
    };

    const activity = (state: IrisActivityState, id = state.toLowerCase(), name = 'lecture_content_retrieval'): IrisActivityItem => ({
        id,
        kind: IrisActivityKind.TOOL,
        name,
        state,
        detail: 'Looking through lecture units',
        result: state === IrisActivityState.FINISHED ? '3 sections' : undefined,
        durationMillis: state === IrisActivityState.FINISHED ? 3100 : undefined,
    });

    const translations: Record<string, string> = {
        'artemisApp.iris.activities.lecture_content_retrieval': 'Lecture search',
        'artemisApp.iris.activities.states.running': 'In progress',
        'artemisApp.iris.activities.states.finished': 'Done',
        'artemisApp.iris.activities.states.failed': 'Failed',
    };

    const ariaLabelOf = (selector: string): string => {
        const node = fixture.debugElement.query(By.css(selector)).nativeElement as HTMLElement;
        return node.getAttribute('aria-label') ?? '';
    };

    beforeEach(async () => {
        translateService = {
            instant: vi.fn((key: string) => translations[key] ?? key),
            getCurrentLang: vi.fn().mockReturnValue('en'),
            onTranslationChange: new Subject(),
            onLangChange: new Subject(),
            onDefaultLangChange: new Subject(),
        };

        await TestBed.configureTestingModule({
            imports: [IrisActivityFeedComponent],
            providers: [{ provide: TranslateService, useValue: translateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisActivityFeedComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render running, finished, and failed activity rows with state icons and labels', async () => {
        fixture.componentRef.setInput('activities', [
            activity(IrisActivityState.RUNNING, 'act-running'),
            activity(IrisActivityState.FINISHED, 'act-finished'),
            activity(IrisActivityState.FAILED, 'act-failed'),
        ]);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.queryAll(By.css('.iris-activity-row'))).toHaveLength(3);
        expect(fixture.debugElement.query(By.css('.iris-activity-row.running .iris-activity-icon'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('.iris-activity-row.finished .iris-activity-icon'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('.iris-activity-row.failed .iris-activity-icon'))).toBeTruthy();
        // Each row names its step, so tests can tell two runs of the same tool apart.
        expect(fixture.debugElement.query(By.css('.iris-activity-row[data-activity-id="act-failed"]'))).toBeTruthy();
        expect(fixture.nativeElement.textContent).toContain('Lecture search');
        expect(fixture.nativeElement.textContent).toContain('3.1s');
    });

    it('should start collapsed, with one summary line that counts the steps', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1'), activity(IrisActivityState.FINISHED, 'act-2')]);
        fixture.componentRef.setInput('mode', 'trail');
        fixture.detectChanges();

        const group = fixture.nativeElement.querySelector('details.iris-activity-group') as HTMLDetailsElement;
        expect(group.open).toBe(false);
        expect(translateService.instant).toHaveBeenCalledWith('artemisApp.iris.activities.trailSummaryPlural', { count: 2, duration: '6.2' });
        expect(group.querySelector('summary')?.textContent?.trim()).toBe('artemisApp.iris.activities.trailSummaryPlural');
    });

    it('should name the running step in the live summary', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1'), activity(IrisActivityState.RUNNING, 'act-2')]);
        fixture.componentRef.setInput('mode', 'live');
        fixture.detectChanges();

        const summary = fixture.nativeElement.querySelector('summary') as HTMLElement;
        expect(summary.textContent?.trim()).toBe('Lecture search');
        expect(summary.querySelector('.iris-activity-summary-spinner')).toBeTruthy();
    });

    it('should count the steps in a saved group even if one never finished', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.RUNNING, 'act-1')]);
        fixture.componentRef.setInput('mode', 'trail');
        fixture.detectChanges();

        const summary = fixture.nativeElement.querySelector('summary') as HTMLElement;
        expect(summary.textContent?.trim()).toBe('artemisApp.iris.activities.trailSummaryWithoutDurationSingular');
        expect(summary.querySelector('.iris-activity-summary-spinner')).toBeFalsy();
    });

    it('should expose the activity label as an aria-label on the row for accessibility', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1')]);
        fixture.detectChanges();

        expect(ariaLabelOf('.iris-activity-row')).toContain('Lecture search');
    });

    it('should render the activity detail in the visible label and accessible name', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1')]);
        fixture.detectChanges();

        // Detail is shown in full as part of the visible row.
        const detail = fixture.debugElement.query(By.css('.iris-activity-detail')).nativeElement as HTMLElement;
        expect(detail.textContent).toContain('Looking through lecture units');

        // ...and folded into the row's accessible name alongside the activity name.
        const ariaLabel = ariaLabelOf('.iris-activity-row');
        expect(ariaLabel).toContain('Lecture search');
        expect(ariaLabel).toContain('Looking through lecture units');
    });

    it.each([
        [IrisActivityState.RUNNING, 'running', 'In progress'],
        [IrisActivityState.FINISHED, 'finished', 'Done'],
        [IrisActivityState.FAILED, 'failed', 'Failed'],
    ])('should announce the %s state in the accessible name of the row', (state, stateClass, stateLabel) => {
        fixture.componentRef.setInput('activities', [activity(state, `act-${stateClass}`)]);
        fixture.detectChanges();

        // The state is conveyed visually only by the row icon, so it must be part of the accessible name.
        expect(ariaLabelOf(`.iris-activity-row.${stateClass}`)).toContain(stateLabel);
    });

    it('should order the accessible name as name, detail, state, and duration', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1')]);
        fixture.detectChanges();

        expect(ariaLabelOf('.iris-activity-row')).toBe('Lecture search · Looking through lecture units · Done · 3.1s');
    });

    it('should fall back to a prettified state when the state translation is missing', () => {
        translateService.instant.mockImplementation((key: string) => key);
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.RUNNING, 'act-1')]);
        fixture.detectChanges();

        const ariaLabel = ariaLabelOf('.iris-activity-row');
        expect(ariaLabel).toContain('Running');
        expect(ariaLabel).not.toContain('artemisApp.iris.activities.states.running');
    });

    it('should prettify missing activity translations instead of rendering raw keys', () => {
        translateService.instant.mockImplementation((key: string) => key);
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.RUNNING, 'act-unknown', 'unknown_tool_name')]);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Unknown tool name');
        expect(fixture.nativeElement.textContent).not.toContain('unknown_tool_name');
    });

    it('should expose live and trail modes through stable classes and aria labels', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.RUNNING)]);
        fixture.componentRef.setInput('mode', 'trail');
        fixture.detectChanges();

        const feed = fixture.debugElement.query(By.css('.iris-activity-group'));
        expect(feed.nativeElement.classList).toContain('mode-trail');
        expect(feed.nativeElement.getAttribute('aria-label')).toBe('artemisApp.iris.activities.trailLabel');
        // Only the live group announces changes.
        expect(feed.nativeElement.getAttribute('aria-live')).toBeNull();

        fixture.componentRef.setInput('mode', 'live');
        fixture.detectChanges();

        expect(feed.nativeElement.classList).toContain('mode-live');
        expect(feed.nativeElement.getAttribute('aria-live')).toBe('polite');
    });

    it('should hide the duration for sub-100ms tool runs', () => {
        fixture.componentRef.setInput('activities', [
            { id: 'act-1', kind: IrisActivityKind.TOOL, name: 'get_course_details', state: IrisActivityState.FINISHED, durationMillis: 40 },
        ]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.iris-activity-duration')).toBeNull();
    });

    it('should render nothing when there are no activities', () => {
        fixture.componentRef.setInput('activities', []);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.iris-activity-group'))).toBeFalsy();
        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });
});

describe('prettifyActivityName', () => {
    it('should convert snake case tool names to sentence case labels', () => {
        expect(prettifyActivityName('lecture_content_retrieval')).toBe('Lecture content retrieval');
    });
});
