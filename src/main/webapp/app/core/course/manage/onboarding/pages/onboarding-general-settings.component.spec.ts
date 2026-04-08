import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { OnboardingGeneralSettingsComponent } from './onboarding-general-settings.component';
import { Course, Language } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { of } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';

describe('OnboardingGeneralSettingsComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: OnboardingGeneralSettingsComponent;
    let fixture: ComponentFixture<OnboardingGeneralSettingsComponent>;
    let course: Course;

    beforeEach(async () => {
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.description = 'Test description';
        course.color = '#3fc0f0';

        await TestBed.configureTestingModule({
            imports: [OnboardingGeneralSettingsComponent, FormsModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
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

        fixture = TestBed.createComponent(OnboardingGeneralSettingsComponent);
        fixture.componentRef.setInput('course', course);
        comp = fixture.componentInstance;
        fixture.detectChanges();
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
});
