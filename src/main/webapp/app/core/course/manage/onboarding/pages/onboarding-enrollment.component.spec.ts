import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { OnboardingEnrollmentComponent } from './onboarding-enrollment.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('OnboardingEnrollmentComponent', () => {
    setupTestBed({ zoneless: true });

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
            imports: [OnboardingEnrollmentComponent, FormsModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(OnboardingEnrollmentComponent, {
                remove: { imports: [TranslateDirective] },
                add: { imports: [MockDirective(TranslateDirective)] },
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
