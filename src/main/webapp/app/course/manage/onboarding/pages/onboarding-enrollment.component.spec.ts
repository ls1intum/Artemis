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

        const expectFormLabel = (inputId: string, key: string) => {
            const label = fixture.nativeElement.querySelector(`label[for="${inputId}"]`) as HTMLLabelElement | null;
            expect(label).not.toBeNull();
            expect(label!.classList).toContain('form-label');
            expect(label!.getAttribute('jhiTranslate') ?? label!.getAttribute('ng-reflect-jhi-translate')).toBe(key);
            expect(pickerById(inputId).componentInstance.hideLabelName()).toBe(true);
        };

        it('should only render the enrollment dates when enrollment is enabled', () => {
            expect(pickers()).toHaveLength(0);

            course.enrollmentEnabled = true;
            fixture.componentRef.setInput('course', Course.from(course));
            fixture.detectChanges();

            expect(pickers().map((picker) => picker.componentInstance.inputId())).toEqual(['onboarding-enrollmentStartDate', 'onboarding-enrollmentEndDate']);
        });

        it('should label each picker with a form label bound to its input, like the fields around it', () => {
            course.enrollmentEnabled = true;
            course.unenrollmentEnabled = true;
            fixture.componentRef.setInput('course', Course.from(course));
            fixture.detectChanges();

            expectFormLabel('onboarding-enrollmentStartDate', 'artemisApp.course.enrollmentStartDate');
            expectFormLabel('onboarding-enrollmentEndDate', 'artemisApp.course.enrollmentEndDate');
            expectFormLabel('onboarding-unenrollmentEndDate', 'artemisApp.course.unenrollmentEndDate');
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
