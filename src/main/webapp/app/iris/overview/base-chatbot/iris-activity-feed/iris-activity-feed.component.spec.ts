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

    beforeEach(async () => {
        translateService = {
            instant: vi.fn((key: string) => (key === 'artemisApp.iris.activities.lecture_content_retrieval' ? 'Lecture search' : key)),
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

    it('should render running, finished, and failed activity chips with text content', async () => {
        fixture.componentRef.setInput('activities', [
            activity(IrisActivityState.RUNNING, 'act-running'),
            activity(IrisActivityState.FINISHED, 'act-finished'),
            activity(IrisActivityState.FAILED, 'act-failed'),
        ]);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.queryAll(By.css('.activity-chip'))).toHaveLength(3);
        expect(fixture.debugElement.query(By.css('.activity-chip.running fa-icon'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('.activity-chip.finished fa-icon'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('.activity-chip.failed fa-icon'))).toBeTruthy();
        expect(fixture.nativeElement.textContent).toContain('Lecture search');
        expect(fixture.nativeElement.textContent).toContain('3.1s');
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

        const feed = fixture.debugElement.query(By.css('.iris-activity-feed'));
        expect(feed.nativeElement.classList).toContain('mode-trail');
        expect(feed.nativeElement.getAttribute('aria-label')).toBe('artemisApp.iris.activities.trailLabel');

        fixture.componentRef.setInput('mode', 'live');
        fixture.detectChanges();

        expect(feed.nativeElement.classList).toContain('mode-live');
    });

    it('should hide the duration badge for sub-100ms tool runs', () => {
        fixture.componentRef.setInput('activities', [
            { id: 'act-1', kind: IrisActivityKind.TOOL, name: 'get_course_details', state: IrisActivityState.FINISHED, durationMillis: 40 },
        ]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.activity-duration')).toBeNull();
    });

    it('should render nothing when there are no activities', () => {
        fixture.componentRef.setInput('activities', []);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.iris-activity-feed'))).toBeFalsy();
        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });
});

describe('prettifyActivityName', () => {
    it('should convert snake case tool names to sentence case labels', () => {
        expect(prettifyActivityName('lecture_content_retrieval')).toBe('Lecture content retrieval');
    });
});
