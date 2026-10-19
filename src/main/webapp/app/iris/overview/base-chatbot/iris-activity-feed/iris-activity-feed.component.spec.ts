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

    it('should render running, finished, and failed activity steps with node icons and labels', async () => {
        fixture.componentRef.setInput('activities', [
            activity(IrisActivityState.RUNNING, 'act-running'),
            activity(IrisActivityState.FINISHED, 'act-finished'),
            activity(IrisActivityState.FAILED, 'act-failed'),
        ]);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.queryAll(By.css('.stepper-step'))).toHaveLength(3);
        expect(fixture.debugElement.query(By.css('.stepper-step.running .stepper-node fa-icon'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('.stepper-step.finished .stepper-node fa-icon'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('.stepper-step.failed .stepper-node fa-icon'))).toBeTruthy();
        // Each node has a persistent label shown below it
        expect(fixture.debugElement.query(By.css('.stepper-label'))).toBeTruthy();
        expect(fixture.nativeElement.textContent).toContain('Lecture search');
        expect(fixture.nativeElement.textContent).toContain('3.1s');
    });

    it('should draw a connector between steps but not after the last one', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1'), activity(IrisActivityState.RUNNING, 'act-2')]);
        fixture.detectChanges();

        // Two steps → exactly one connector
        expect(fixture.debugElement.queryAll(By.css('.stepper-connector'))).toHaveLength(1);
    });

    it('should expose the activity label as an aria-label on the node for accessibility', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1')]);
        fixture.detectChanges();

        expect(ariaLabelOf('.stepper-node')).toContain('Lecture search');
    });

    it('should render the activity detail in the visible label and accessible name', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1')]);
        fixture.detectChanges();

        // Detail is shown in full as part of the visible stepper label.
        const detail = fixture.debugElement.query(By.css('.stepper-label-detail')).nativeElement as HTMLElement;
        expect(detail.textContent).toContain('Looking through lecture units');

        // ...and folded into the node's accessible name alongside the activity name.
        const ariaLabel = ariaLabelOf('.stepper-node');
        expect(ariaLabel).toContain('Lecture search');
        expect(ariaLabel).toContain('Looking through lecture units');
    });

    it.each([
        [IrisActivityState.RUNNING, 'running', 'In progress'],
        [IrisActivityState.FINISHED, 'finished', 'Done'],
        [IrisActivityState.FAILED, 'failed', 'Failed'],
    ])('should announce the %s state in the accessible name of the node', (state, stateClass, stateLabel) => {
        fixture.componentRef.setInput('activities', [activity(state, `act-${stateClass}`)]);
        fixture.detectChanges();

        // The state is conveyed visually only by the node icon, so it must be part of the accessible name.
        expect(ariaLabelOf(`.stepper-step.${stateClass} .stepper-node`)).toContain(stateLabel);
    });

    it('should order the accessible name as name, detail, state, and duration', () => {
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.FINISHED, 'act-1')]);
        fixture.detectChanges();

        expect(ariaLabelOf('.stepper-node')).toBe('Lecture search · Looking through lecture units · Done · 3.1s');
    });

    it('should fall back to a prettified state when the state translation is missing', () => {
        translateService.instant.mockImplementation((key: string) => key);
        fixture.componentRef.setInput('activities', [activity(IrisActivityState.RUNNING, 'act-1')]);
        fixture.detectChanges();

        const ariaLabel = ariaLabelOf('.stepper-node');
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

        const feed = fixture.debugElement.query(By.css('.iris-activity-stepper'));
        expect(feed.nativeElement.classList).toContain('mode-trail');
        expect(feed.nativeElement.getAttribute('aria-label')).toBe('artemisApp.iris.activities.trailLabel');

        fixture.componentRef.setInput('mode', 'live');
        fixture.detectChanges();

        expect(feed.nativeElement.classList).toContain('mode-live');
    });

    it('should hide the duration for sub-100ms tool runs', () => {
        fixture.componentRef.setInput('activities', [
            { id: 'act-1', kind: IrisActivityKind.TOOL, name: 'get_course_details', state: IrisActivityState.FINISHED, durationMillis: 40 },
        ]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.stepper-label-duration')).toBeNull();
    });

    it('should render nothing when there are no activities', () => {
        fixture.componentRef.setInput('activities', []);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.iris-activity-stepper'))).toBeFalsy();
        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });
});

describe('prettifyActivityName', () => {
    it('should convert snake case tool names to sentence case labels', () => {
        expect(prettifyActivityName('lecture_content_retrieval')).toBe('Lecture content retrieval');
    });
});
