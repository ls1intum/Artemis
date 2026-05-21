import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseUnenrollmentModalComponent } from 'app/core/course/overview/course-unenrollment-modal/course-unenrollment-modal.component';
import { Course } from 'app/core/course/shared/entities/course.model';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Router, provideRouter } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseUnenrollmentModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseUnenrollmentModalComponent>;
    let component: CourseUnenrollmentModalComponent;
    let courseService: CourseManagementService;
    let unenrollFromCourseStub: ReturnType<typeof vi.spyOn>;
    let alertService: AlertService;
    let successAlertStub: ReturnType<typeof vi.spyOn>;
    let errorAlertStub: ReturnType<typeof vi.spyOn>;
    let router: Router;
    let navigateStub: ReturnType<typeof vi.spyOn>;

    const testCourse = new Course();

    beforeEach(async () => {
        testCourse.id = 1;
        testCourse.title = 'Unenrollment Test Course Title';

        TestBed.configureTestingModule({
            imports: [CourseUnenrollmentModalComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [provideRouter([]), MockProvider(CourseManagementService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseUnenrollmentModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', testCourse);
        courseService = TestBed.inject(CourseManagementService);
        unenrollFromCourseStub = vi.spyOn(courseService, 'unenrollFromCourse').mockReturnValue(of(new HttpResponse({ body: ['student-group-name'] })));
        alertService = TestBed.inject(AlertService);
        successAlertStub = vi.spyOn(alertService, 'success');
        errorAlertStub = vi.spyOn(alertService, 'error');
        router = TestBed.inject(Router);
        navigateStub = vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should disable unenrollment button when confirmation input is invalid', () => {
        component.visible.set(true);
        fixture.detectChanges();
        // p-dialog with appendTo="'body'" renders content in document.body, not inside fixture.nativeElement
        const confirmationInput = document.querySelector<HTMLInputElement>('input')!;
        const unenrollButton = document.querySelector<HTMLButtonElement>('#course-unenrollment-accept-button')!;
        fixture.detectChanges();
        expect(unenrollButton.disabled).toBe(true);
        const inputEvent = new Event('input');
        confirmationInput.value = 'Some invalid Title';
        confirmationInput.dispatchEvent(inputEvent);
        fixture.detectChanges();
        expect(unenrollButton.disabled).toBe(true);
    });

    it('should enable unenrollment button when confirmation input is valid', () => {
        component.visible.set(true);
        fixture.detectChanges();
        // p-dialog with appendTo="'body'" renders content in document.body, not inside fixture.nativeElement
        const confirmationInput = document.querySelector<HTMLInputElement>('input')!;
        const unenrollButton = document.querySelector<HTMLButtonElement>('#course-unenrollment-accept-button')!;
        const inputEvent = new Event('input');
        confirmationInput.value = 'Unenrollment Test Course Title';
        confirmationInput.dispatchEvent(inputEvent);
        fixture.detectChanges();
        expect(unenrollButton.disabled).toBe(false);
    });

    it('should alert success after unenrollment', () => {
        component.onUnenroll();
        expect(unenrollFromCourseStub).toHaveBeenCalledOnce();
        expect(successAlertStub).toHaveBeenCalledOnce();
    });

    it('should navigate home after unenrollment', () => {
        component.onUnenroll();
        expect(navigateStub).toHaveBeenCalledExactlyOnceWith(['/']);
    });

    it('should alert error after unsuccessful unenrollment', () => {
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        unenrollFromCourseStub = vi.spyOn(courseService, 'unenrollFromCourse').mockReturnValue(throwError(() => httpError));
        fixture.changeDetectorRef.detectChanges();
        component.onUnenroll();
        expect(unenrollFromCourseStub).toHaveBeenCalledOnce();
        expect(errorAlertStub).toHaveBeenCalledOnce();
    });

    it('should report student can enroll again for valid', () => {
        const courseWithEnrollment = new Course();
        courseWithEnrollment.id = 1;
        courseWithEnrollment.title = 'Unenrollment Test Course Title';
        courseWithEnrollment.unenrollmentEnabled = true;
        courseWithEnrollment.enrollmentEnabled = true;
        courseWithEnrollment.enrollmentEndDate = dayjs().add(1, 'day');
        fixture.componentRef.setInput('course', courseWithEnrollment);
        fixture.changeDetectorRef.detectChanges();
        expect(component.canEnrollAgain).toBe(true);
    });

    it('should not report student can enroll again for invalid', () => {
        const courseWithEnrollment = new Course();
        courseWithEnrollment.id = 1;
        courseWithEnrollment.title = 'Unenrollment Test Course Title';
        courseWithEnrollment.unenrollmentEnabled = true;
        courseWithEnrollment.enrollmentEnabled = true;
        courseWithEnrollment.enrollmentEndDate = dayjs().subtract(1, 'day');
        fixture.componentRef.setInput('course', courseWithEnrollment);
        fixture.changeDetectorRef.detectChanges();
        expect(component.canEnrollAgain).toBe(false);
    });

    it('should set visible to false when onCancel is called', () => {
        component.visible.set(true);
        component.onCancel();
        expect(component.visible()).toBe(false);
    });
});
