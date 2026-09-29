import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AthenaSettingsUpdateComponent } from 'app/course/manage/athena-settings-update/athena-settings-update.component';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular';
import { ATHENA_FEEDBACK_STYLE_EXAMPLES } from 'app/course/manage/athena-settings-update/athena-feedback-style-examples';

describe('AthenaSettingsUpdateComponent', () => {
    let fixture: ComponentFixture<AthenaSettingsUpdateComponent>;
    let comp: AthenaSettingsUpdateComponent;
    let athenaCourseConfigService: AthenaCourseConfigService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: '5' });

    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

    function createComponent() {
        fixture = TestBed.createComponent(AthenaSettingsUpdateComponent);
        comp = fixture.componentInstance;
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AthenaSettingsUpdateComponent, TranslatePipeMock],
            providers: [
                MockProvider(AthenaCourseConfigService),
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: { params: routeParamsSubject.asObservable() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load the configuration of the course named by the route', () => {
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }));
        createComponent();
        fixture.detectChanges();

        expect(getSpy).toHaveBeenCalledExactlyOnceWith(5);
        expect(comp.gradingEnabled()).toBe(true);
        expect(comp.formativeEnabled()).toBe(false);
    });

    it('should render one toggle per feature', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        createComponent();
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        expect(element.querySelector('[data-testid="athena-settings-formative-feedback"]')).toBeTruthy();
        expect(element.querySelector('[data-testid="athena-settings-grading-feedback"]')).toBeTruthy();
    });

    it.each([
        { feature: 'formativeFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true } },
        { feature: 'gradingFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } },
    ])('should save $feature without touching the other feature', ({ feature, expected }) => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: expected })));
        createComponent();
        fixture.detectChanges();

        comp.setEnabled(feature, true);

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { [feature]: true });
        expect(comp.formativeEnabled()).toBe(expected.formativeFeedbackEnabled);
        expect(comp.gradingEnabled()).toBe(expected.gradingFeedbackEnabled);
    });

    it.each([
        { testId: 'athena-settings-formative-feedback', feature: 'formativeFeedbackEnabled' },
        { testId: 'athena-settings-grading-feedback', feature: 'gradingFeedbackEnabled' },
    ])('should save $feature when its toggle is switched', ({ testId, feature }) => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: { ...bothDisabled, [feature]: true } })));
        createComponent();
        fixture.detectChanges();

        fixture.debugElement.query(By.css(`[data-testid="${testId}"]`)).triggerEventHandler('changed', true);

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { [feature]: true });
    });

    it('should name each toggle after its feature rather than its state', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }));
        createComponent();
        fixture.detectChanges();

        const toggles = fixture.debugElement.queryAll(By.directive(TumAetUiToggleSwitchComponent)).map((toggle) => toggle.componentInstance as TumAetUiToggleSwitchComponent);
        expect(toggles.map((toggle) => toggle.ariaLabel())).toEqual([
            'artemisApp.course.athenaConfig.gradingFeedbackEnabled.label',
            'artemisApp.course.athenaConfig.formativeFeedbackEnabled.label',
        ]);
    });

    it('should save a clicked feedback style tick', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: { ...bothDisabled, defaultFeedbackDetail: 3 } })));
        createComponent();
        fixture.detectChanges();

        comp.selectFeedbackStyleLevel('defaultFeedbackDetail', 3);

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { defaultFeedbackDetail: 3 });
        expect(comp.defaultFeedbackDetail()).toBe(3);
    });

    it('should leave the level alone when its tick is clicked again', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, defaultFeedbackFormality: 1 }));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig');
        createComponent();
        fixture.detectChanges();

        comp.selectFeedbackStyleLevel('defaultFeedbackFormality', 1);

        expect(updateSpy).not.toHaveBeenCalled();
        expect(comp.defaultFeedbackFormality()).toBe(1);
    });

    it('should show a course without a default as neutral and keep it without one when neutral is clicked', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true }));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig');
        createComponent();
        fixture.detectChanges();

        const slider: HTMLElement = fixture.nativeElement.querySelector('[data-testid="athena-settings-default-feedback-detail-slider"]');
        expect(slider.getAttribute('aria-valuenow')).toBe('2');
        expect(comp.feedbackDetailLevel()).toBe(2);

        comp.selectFeedbackStyleLevel('defaultFeedbackDetail', 2);

        expect(updateSpy).not.toHaveBeenCalled();
        expect(comp.defaultFeedbackDetail()).toBe(0);
    });

    it.each([
        { key: 'ArrowRight', stored: 2, expected: 3 },
        { key: 'ArrowUp', stored: 1, expected: 2 },
        { key: 'ArrowLeft', stored: 2, expected: 1 },
        { key: 'ArrowDown', stored: 3, expected: 2 },
        { key: 'Home', stored: 3, expected: 1 },
        { key: 'End', stored: 1, expected: 3 },
    ])('should move the slider with $key', ({ key, stored, expected }) => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true, defaultFeedbackDetail: stored }));
        const updateSpy = vi
            .spyOn(athenaCourseConfigService, 'updateCourseConfig')
            .mockReturnValue(of(new HttpResponse({ body: { ...bothDisabled, formativeFeedbackEnabled: true, defaultFeedbackDetail: expected } })));
        createComponent();
        fixture.detectChanges();

        const slider: HTMLElement = fixture.nativeElement.querySelector('[data-testid="athena-settings-default-feedback-detail-slider"]');
        const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
        slider.dispatchEvent(event);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(true);
        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { defaultFeedbackDetail: expected });
        expect(slider.getAttribute('aria-valuenow')).toBe(String(expected));
    });

    it('should save a clicked tick and leave the focus on the slider rather than the tick', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true }));
        const updateSpy = vi
            .spyOn(athenaCourseConfigService, 'updateCourseConfig')
            .mockReturnValue(of(new HttpResponse({ body: { ...bothDisabled, formativeFeedbackEnabled: true, defaultFeedbackDetail: 3 } })));
        createComponent();
        fixture.detectChanges();

        const tick: HTMLElement = fixture.nativeElement.querySelector('[data-testid="athena-settings-default-feedback-detail-3"]');
        const mousedown = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
        tick.dispatchEvent(mousedown);
        tick.click();
        fixture.detectChanges();

        // The tick is hidden from assistive technology, so it must not keep the focus; the slider takes it instead.
        expect(mousedown.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(fixture.nativeElement.querySelector('[data-testid="athena-settings-default-feedback-detail-slider"]'));
        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { defaultFeedbackDetail: 3 });
    });

    it('should stay at the end of the slider and leave other keys to the browser', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true, defaultFeedbackDetail: 3 }));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig');
        createComponent();
        fixture.detectChanges();

        const slider: HTMLElement = fixture.nativeElement.querySelector('[data-testid="athena-settings-default-feedback-detail-slider"]');
        slider.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }));
        const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
        slider.dispatchEvent(tab);

        expect(updateSpy).not.toHaveBeenCalled();
        expect(tab.defaultPrevented).toBe(false);
    });

    it('should roll a feedback style default back to the stored value when saving it fails', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, defaultFeedbackDetail: 2 }));
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        createComponent();
        fixture.detectChanges();

        comp.selectFeedbackStyleLevel('defaultFeedbackDetail', 3);

        expect(comp.defaultFeedbackDetail()).toBe(2);
    });

    it('should render a clickable tick per level for both feedback style defaults while formative feedback is on', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true }));
        createComponent();
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        const section = element.querySelector('[data-testid="athena-settings-feedback-style"]');
        // The section sits inside the formative feedback card, since the style only applies to those requests.
        expect(section?.closest('tumaet-ui-card')?.querySelector('[data-testid="athena-settings-formative-feedback"]')).toBeTruthy();
        // Each style sits in a box of its own, holding its own ticks.
        for (const field of ['default-feedback-detail', 'default-feedback-formality']) {
            const box = section?.querySelector(`[data-testid="athena-settings-${field}"]`);
            for (const level of [1, 2, 3]) {
                expect(box?.querySelector(`[data-testid="athena-settings-${field}-${level}"]`)).toBeTruthy();
            }
        }
    });

    it('should hide the feedback style defaults while formative feedback is off', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, gradingFeedbackEnabled: true, defaultFeedbackDetail: 3 }));
        createComponent();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="athena-settings-feedback-style"]')).toBeNull();
    });

    it('should keep the stored feedback style defaults when formative feedback is switched off', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true, defaultFeedbackDetail: 3 }));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: { ...bothDisabled, defaultFeedbackDetail: 3 } })));
        createComponent();
        fixture.detectChanges();

        comp.setEnabled('formativeFeedbackEnabled', false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="athena-settings-feedback-style"]')).toBeNull();
        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { formativeFeedbackEnabled: false });
        expect(comp.defaultFeedbackDetail()).toBe(3);
    });

    it('should show the feedback item Athena returned for the selected level, where a student result puts it', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true, defaultFeedbackDetail: 3 }));
        createComponent();
        fixture.detectChanges();

        const athenaOutput = ATHENA_FEEDBACK_STYLE_EXAMPLES['en'].defaultFeedbackDetail[3];
        const result = comp.feedbackDetailExample();
        expect(result.submission).toEqual({ text: ATHENA_FEEDBACK_STYLE_EXAMPLES['en'].submission });
        expect(result.feedbacks).toHaveLength(1);
        const feedback = result.feedbacks![0];
        expect(feedback.text).toBe(athenaOutput.title);
        expect(feedback.detailText).toBe(athenaOutput.description);
        expect(feedback.credits).toBe(athenaOutput.credits);
        expect(feedback.reference).toBe(athenaOutput.reference);
    });

    it('should show the neutral example while no course default is set', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true }));
        createComponent();
        fixture.detectChanges();

        expect(comp.feedbackFormalityExample().feedbacks![0].text).toBe(ATHENA_FEEDBACK_STYLE_EXAMPLES['en'].defaultFeedbackFormality[2].title);
    });

    it('should show the German example for German and fall back to English for other languages', () => {
        const translateService = TestBed.inject(TranslateService);
        const currentLang = vi.spyOn(translateService, 'getCurrentLang').mockReturnValue('de');
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true }));
        createComponent();
        fixture.detectChanges();
        expect(comp.feedbackDetailExample().submission).toEqual({ text: ATHENA_FEEDBACK_STYLE_EXAMPLES['de'].submission });

        currentLang.mockReturnValue('fr');
        fixture.componentRef.destroy();
        createComponent();
        fixture.detectChanges();
        expect(comp.feedbackDetailExample().submission).toEqual({ text: ATHENA_FEEDBACK_STYLE_EXAMPLES['en'].submission });
    });

    it('should attach every example feedback to a sentence of its submission', () => {
        for (const examples of Object.values(ATHENA_FEEDBACK_STYLE_EXAMPLES)) {
            for (const levels of [examples.defaultFeedbackDetail, examples.defaultFeedbackFormality]) {
                for (const level of [1, 2, 3]) {
                    expect(examples.submission).toContain(levels[level].reference);
                }
            }
        }
    });

    it('should render the example with the referenced sentence highlighted and the feedback below it', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, formativeFeedbackEnabled: true }));
        createComponent();
        fixture.detectChanges();

        const example: HTMLElement = fixture.nativeElement.querySelector('[data-testid="athena-settings-default-feedback-detail-example"]');
        expect(example.closest('[data-testid="athena-settings-default-feedback-detail-example-box"]')).toBeTruthy();
        expect(example.textContent).toContain(ATHENA_FEEDBACK_STYLE_EXAMPLES['en'].defaultFeedbackDetail[2].reference);
        expect(example.querySelectorAll('jhi-unified-feedback')).toHaveLength(1);
    });
});
