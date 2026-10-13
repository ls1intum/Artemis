import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { OnboardingEnrollmentComponent } from './onboarding-enrollment.component';
import { Course } from 'app/course/shared/entities/course.model';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('OnboardingEnrollmentComponent', () => {
    let comp: OnboardingEnrollmentComponent;
    let fixture: ComponentFixture<OnboardingEnrollmentComponent>;
    let course: Course;

    beforeEach(async () => {
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.enrollmentEnabled = false;
        course.unenrollmentEnabled = false;
        course.onlineCourse = false;

        await TestBed.configureTestingModule({
            imports: [OnboardingEnrollmentComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(OnboardingEnrollmentComponent, {
                remove: { imports: [TranslateDirective, TumAetUiDatePickerComponent] },
                add: { imports: [MockDirective(TranslateDirective), MockComponent(TumAetUiDatePickerComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(OnboardingEnrollmentComponent);
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

    describe('date pickers', () => {
        const pickers = () => fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent));
        const pickerById = (id: string) => pickers().find((picker) => picker.componentInstance.inputId() === id)!;

        const expectPickerLabel = (inputId: string, key: string) => {
            const picker = pickerById(inputId).componentInstance;
            expect(picker.labelName()).toBe(key);
            expect(picker.hideLabelName()).toBeFalsy();
        };

        it('should only render the enrollment dates when enrollment is enabled', () => {
            expect(pickers()).toHaveLength(0);

            course.enrollmentEnabled = true;
            fixture.componentRef.setInput('course', Course.from(course));
            fixture.detectChanges();

            expect(pickers().map((picker) => picker.componentInstance.inputId())).toEqual(['onboarding-enrollmentStartDate', 'onboarding-enrollmentEndDate']);
        });

        it('should label each picker with its own visible label', () => {
            course.enrollmentEnabled = true;
            course.unenrollmentEnabled = true;
            fixture.componentRef.setInput('course', Course.from(course));
            fixture.detectChanges();

            expectPickerLabel('onboarding-enrollmentStartDate', 'artemisApp.course.enrollmentStartDate');
            expectPickerLabel('onboarding-enrollmentEndDate', 'artemisApp.course.enrollmentEndDate');
            expectPickerLabel('onboarding-unenrollmentEndDate', 'artemisApp.course.unenrollmentEndDate');
        });

        it('should render the unenrollment end date once unenrollment is enabled', () => {
            course.enrollmentEnabled = true;
            course.unenrollmentEnabled = true;
            course.unenrollmentEndDate = dayjs('2025-12-01T10:00:00');
            fixture.componentRef.setInput('course', Course.from(course));
            fixture.detectChanges();

            const picker = pickerById('onboarding-unenrollmentEndDate');
            expect(picker.componentInstance.value().isSame(course.unenrollmentEndDate)).toBe(true);
        });

        describe('text validity reported to the wizard', () => {
            let emitSpy: ReturnType<typeof vi.spyOn>;

            const showPickers = (unenrollment: boolean) => {
                course.enrollmentEnabled = true;
                course.unenrollmentEnabled = unenrollment;
                fixture.componentRef.setInput('course', Course.from(course));
                fixture.detectChanges();
                emitSpy = vi.spyOn(comp.dateTextValidityChange, 'emit');
            };
            const setTextValid = (id: string, valid: boolean) => pickerById(id).componentInstance.inputValidityChange.emit(valid);

            it('should report invalid text in the enrollment start or end date until both are dates again', () => {
                showPickers(false);

                setTextValid('onboarding-enrollmentStartDate', false);
                expect(emitSpy).toHaveBeenLastCalledWith(false);

                // The end date is fine, but the start date still is not.
                setTextValid('onboarding-enrollmentEndDate', true);
                expect(emitSpy).toHaveBeenLastCalledWith(false);

                setTextValid('onboarding-enrollmentEndDate', false);
                setTextValid('onboarding-enrollmentStartDate', true);
                expect(emitSpy).toHaveBeenLastCalledWith(false);

                setTextValid('onboarding-enrollmentEndDate', true);
                expect(emitSpy).toHaveBeenLastCalledWith(true);
            });

            it('should include the unenrollment end date only while unenrollment is enabled', () => {
                showPickers(true);

                setTextValid('onboarding-unenrollmentEndDate', false);
                expect(emitSpy).toHaveBeenLastCalledWith(false);

                // Disabling unenrollment removes the picker with its invalid text, so it no longer counts.
                comp.toggleUnenrollment();
                expect(emitSpy).toHaveBeenLastCalledWith(true);

                // Enabling it again renders a fresh picker, which starts with valid text.
                course.unenrollmentEnabled = true;
                fixture.componentRef.setInput('course', Course.from(course));
                fixture.detectChanges();
                setTextValid('onboarding-unenrollmentEndDate', true);
                expect(emitSpy).toHaveBeenLastCalledWith(true);
            });

            it('should report valid text after enrollment is switched off while a picker held invalid text', () => {
                showPickers(false);
                setTextValid('onboarding-enrollmentStartDate', false);

                comp.toggleEnrollment();

                expect(emitSpy).toHaveBeenLastCalledWith(true);
            });

            it('should report valid text after the course is switched to an online course while a picker held invalid text', () => {
                showPickers(false);
                setTextValid('onboarding-enrollmentEndDate', false);

                comp.toggleOnlineCourse();

                expect(emitSpy).toHaveBeenLastCalledWith(true);
            });
        });

        it('should write a picked date back to the course', () => {
            course.enrollmentEnabled = true;
            fixture.componentRef.setInput('course', Course.from(course));
            fixture.detectChanges();
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');
            const picked = dayjs('2025-10-01T08:00:00');

            pickerById('onboarding-enrollmentStartDate').componentInstance.valueChange.emit(picked);
            pickerById('onboarding-enrollmentEndDate').componentInstance.valueChange.emit(undefined);

            expect(emitSpy.mock.calls[0][0].enrollmentStartDate).toBe(picked);
            expect(emitSpy.mock.calls[1][0].enrollmentEndDate).toBeUndefined();
        });
    });

    describe('updateField', () => {
        it('should emit courseUpdated', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.updateField('title', 'New Title');

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.title).toBe('New Title');
        });
    });

    describe('toggleEnrollment', () => {
        it('should enable enrollment', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleEnrollment();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.enrollmentEnabled).toBe(true);
        });

        it('should disable online course when enabling enrollment', () => {
            course.onlineCourse = true;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleEnrollment();

            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.enrollmentEnabled).toBe(true);
            expect(emitted.onlineCourse).toBe(false);
        });

        it('should clear enrollment fields when disabling enrollment', () => {
            course.enrollmentEnabled = true;
            course.enrollmentConfirmationMessage = 'Some message';
            course.unenrollmentEnabled = true;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleEnrollment();

            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.enrollmentEnabled).toBe(false);
            expect(emitted.enrollmentConfirmationMessage).toBeUndefined();
            expect(emitted.unenrollmentEnabled).toBe(false);
        });
    });

    describe('toggleUnenrollment', () => {
        it('should toggle unenrollment enabled', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleUnenrollment();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.unenrollmentEnabled).toBe(true);
        });
    });

    describe('toggleOnlineCourse', () => {
        it('should enable online course and disable enrollment with cleanup', () => {
            course.enrollmentEnabled = true;
            course.enrollmentConfirmationMessage = 'Some message';
            course.unenrollmentEnabled = true;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleOnlineCourse();

            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.onlineCourse).toBe(true);
            expect(emitted.enrollmentEnabled).toBe(false);
            expect(emitted.enrollmentConfirmationMessage).toBeUndefined();
            expect(emitted.unenrollmentEnabled).toBe(false);
            expect(emitSpy).toHaveBeenCalled();
        });

        it('should disable online course', () => {
            course.onlineCourse = true;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleOnlineCourse();

            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.onlineCourse).toBe(false);
        });
    });

    describe('updateEnrollmentMessage', () => {
        it('should update the enrollment confirmation message', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.updateEnrollmentMessage('Welcome to the course!');

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.enrollmentConfirmationMessage).toBe('Welcome to the course!');
        });
    });
});
