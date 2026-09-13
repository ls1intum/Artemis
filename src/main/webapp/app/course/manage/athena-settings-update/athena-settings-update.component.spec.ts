import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { AthenaSettingsUpdateComponent } from 'app/course/manage/athena-settings-update/athena-settings-update.component';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('AthenaSettingsUpdateComponent', () => {
    let fixture: ComponentFixture<AthenaSettingsUpdateComponent>;
    let comp: AthenaSettingsUpdateComponent;
    let athenaCourseConfigService: AthenaCourseConfigService;
    let accountService: AccountService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: '5' });

    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false, allowedFeedbackRequests: 10 };

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
        accountService = TestBed.inject(AccountService);
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

    it('should render a loading placeholder instead of the toggles before the configuration answers', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(new Subject<AthenaCourseConfigDTO>().asObservable());
        createComponent();
        fixture.detectChanges();

        const element = fixture.nativeElement;
        // The two feature toggles and the two feedback style defaults each show their own loading placeholder.
        expect(element.querySelectorAll('[data-testid$="-loading"]')).toHaveLength(4);
        expect(element.querySelector('[data-testid="athena-settings-formative-feedback-enable"]')).toBeFalsy();
        expect(element.querySelector('[data-testid="athena-settings-grading-feedback-enable"]')).toBeFalsy();
    });

    it('should expose the allowed feedback requests from the loaded configuration', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        createComponent();
        fixture.detectChanges();

        expect(comp.allowedFeedbackRequests()).toBe(10);
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

    it('should show the admin tab for an admin', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);
        createComponent();
        fixture.detectChanges();

        expect(comp.isAdmin()).toBe(true);
    });

    it('should hide the admin tab for a non-admin', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        vi.spyOn(accountService, 'isAdmin').mockReturnValue(false);
        createComponent();
        fixture.detectChanges();

        expect(comp.isAdmin()).toBe(false);
    });
});
