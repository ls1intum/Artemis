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
            'artemisApp.course.athenaConfig.formativeFeedbackEnabled.label',
            'artemisApp.course.athenaConfig.gradingFeedbackEnabled.label',
        ]);
    });

    it('should save a clicked feedback style tick', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: { ...bothDisabled, defaultFeedbackDetail: 3 } })));
        createComponent();
        fixture.detectChanges();

        comp.onFeedbackStyleTickClick('defaultFeedbackDetail', 3);

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { defaultFeedbackDetail: 3 });
        expect(comp.defaultFeedbackDetail()).toBe(3);
    });

    it('should clear a feedback style default when its already-active tick is clicked again', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, defaultFeedbackFormality: 1 }));
        const updateSpy = vi
            .spyOn(athenaCourseConfigService, 'updateCourseConfig')
            .mockReturnValue(of(new HttpResponse({ body: { ...bothDisabled, defaultFeedbackFormality: 0 } })));
        createComponent();
        fixture.detectChanges();

        comp.onFeedbackStyleTickClick('defaultFeedbackFormality', 1);

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { defaultFeedbackFormality: 0 });
        expect(comp.defaultFeedbackFormality()).toBe(0);
    });

    it('should roll a feedback style default back to the stored value when saving it fails', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, defaultFeedbackDetail: 2 }));
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        createComponent();
        fixture.detectChanges();

        comp.onFeedbackStyleTickClick('defaultFeedbackDetail', 3);

        expect(comp.defaultFeedbackDetail()).toBe(2);
    });

    it('should render a clickable tick per level for both feedback style defaults', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        createComponent();
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        for (const field of ['default-feedback-detail', 'default-feedback-formality']) {
            for (const level of [1, 2, 3]) {
                expect(element.querySelector(`[data-testid="athena-settings-${field}-${level}"]`)).toBeTruthy();
            }
        }
    });
});
