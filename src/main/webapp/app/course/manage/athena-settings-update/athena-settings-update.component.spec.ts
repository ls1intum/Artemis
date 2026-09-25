import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { AthenaSettingsUpdateComponent } from 'app/course/manage/athena-settings-update/athena-settings-update.component';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

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
});
