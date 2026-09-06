import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { OnboardingGeneralSettingsComponent } from './onboarding-general-settings.component';
import { Course, Language } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ColorSelectorComponent } from 'app/shared-ui/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ARTEMIS_DEFAULT_COLOR, MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';
import { AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';

describe('OnboardingGeneralSettingsComponent', () => {
    let comp: OnboardingGeneralSettingsComponent;
    let fixture: ComponentFixture<OnboardingGeneralSettingsComponent>;
    let course: Course;
    let athenaCourseConfigService: AthenaCourseConfigService;
    // Read when the component is constructed, so tests that need a module active set it before calling createComponent()
    let activeModuleFeatures: string[];

    function createComponent() {
        fixture = TestBed.createComponent(OnboardingGeneralSettingsComponent);
        fixture.componentRef.setInput('course', course);
        comp = fixture.componentInstance;
        athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        activeModuleFeatures = [];
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.description = 'Test description';
        course.color = '#3fc0f0';

        await TestBed.configureTestingModule({
            imports: [OnboardingGeneralSettingsComponent, FormsModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ProfileService,
                    useValue: Object.assign(new MockProfileService(), { isModuleFeatureActive: (feature: string) => activeModuleFeatures.includes(feature) }),
                },
                {
                    provide: AthenaCourseConfigService,
                    useValue: {
                        getCourseConfig: () => of({ gradingFeedbackEnabled: false, formativeFeedbackEnabled: false }),
                        updateCourseConfig: () => of(new HttpResponse({ body: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false } })),
                    },
                },
                {
                    provide: IrisSettingsService,
                    useValue: {
                        getCourseSettingsWithRateLimit: () => of({ settings: { enabled: true } }),
                        updateCourseSettings: () => of({ body: { settings: { enabled: false } } }),
                    },
                },
                MockProvider(DialogService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(OnboardingGeneralSettingsComponent, {
                remove: {
                    imports: [TranslateDirective, ArtemisTranslatePipe, ColorSelectorComponent, FormDateTimePickerComponent],
                },
                add: {
                    imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockComponent(ColorSelectorComponent), MockComponent(FormDateTimePickerComponent)],
                },
            })
            .compileComponents();

        createComponent();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with the provided course', () => {
        expect(comp).toBeTruthy();
        expect(comp.course()).toEqual(course);
    });

    it('should have language options for English and German', () => {
        expect(comp.languageOptions).toHaveLength(2);
        expect(comp.languageOptions[0].key).toBe(Language.ENGLISH);
        expect(comp.languageOptions[1].key).toBe(Language.GERMAN);
    });

    it('should have semesters available', () => {
        expect(comp.semesters).toBeDefined();
        expect(comp.semesters.length).toBeGreaterThan(0);
    });

    it('should expose ARTEMIS_DEFAULT_COLOR', () => {
        expect(comp.ARTEMIS_DEFAULT_COLOR).toBe(ARTEMIS_DEFAULT_COLOR);
    });

    describe('updateField', () => {
        it('should emit courseUpdated when a field changes', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.updateField('description', 'new value');

            expect(emitSpy).toHaveBeenCalled();
            const emittedCourse = emitSpy.mock.calls[0][0];
            expect(emittedCourse.description).toBe('new value');
        });
    });

    describe('openColorSelector', () => {
        it('should delegate to ColorSelectorComponent', () => {
            const colorSelectorRef = comp.colorSelector();
            const openSpy = vi.spyOn(colorSelectorRef!, 'openColorSelector');

            const event = new MouseEvent('click');
            comp.openColorSelector(event);

            expect(openSpy).toHaveBeenCalledWith(event);
        });
    });

    describe('onSelectedColor', () => {
        it('should update the course color and emit change', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.onSelectedColor('#ff0000');

            expect(emitSpy).toHaveBeenCalled();
            const emittedCourse = emitSpy.mock.calls[0][0];
            expect(emittedCourse.color).toBe('#ff0000');
        });
    });
    describe('athena configuration', () => {
        function createWithAthenaActive() {
            activeModuleFeatures = [MODULE_FEATURE_ATHENA];
            createComponent();
        }

        it('should not load the athena configuration when the module is inactive', () => {
            const getSpy = vi.spyOn(TestBed.inject(AthenaCourseConfigService), 'getCourseConfig');
            createComponent();

            expect(comp.athenaEnabled).toBe(false);
            expect(getSpy).not.toHaveBeenCalled();
        });

        it('should load the athena configuration when the module is active', () => {
            activeModuleFeatures = [MODULE_FEATURE_ATHENA];
            fixture = TestBed.createComponent(OnboardingGeneralSettingsComponent);
            fixture.componentRef.setInput('course', course);
            comp = fixture.componentInstance;
            athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
            const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }));
            fixture.detectChanges();

            expect(getSpy).toHaveBeenCalledExactlyOnceWith(1);
            expect(comp.isAthenaGradingEnabled()).toBe(true);
            expect(comp.isAthenaFormativeEnabled()).toBe(false);
        });

        it('should save a switched feature right away without touching the other one', () => {
            createWithAthenaActive();
            const expected = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true };
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: expected })));

            comp.setAthenaFeatureEnabled('formativeFeedbackEnabled', true);

            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(1, expected);
            expect(comp.isAthenaFormativeEnabled()).toBe(true);
            expect(comp.isAthenaGradingEnabled()).toBe(false);
        });

        it('should not emit a course update when a feature is switched', () => {
            createWithAthenaActive();
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(
                of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } })),
            );
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.setAthenaFeatureEnabled('gradingFeedbackEnabled', true);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should revert the feature and alert when saving fails', () => {
            createWithAthenaActive();
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            comp.setAthenaFeatureEnabled('gradingFeedbackEnabled', true);

            expect(comp.isAthenaGradingEnabled()).toBe(false);
            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
        });
    });
});
