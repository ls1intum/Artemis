import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseUnenrollmentModalComponent } from 'app/core/course/overview/course-unenrollment-modal/course-unenrollment-modal.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Router, provideRouter } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseRegistrationButtonComponent', () => {
    let fixture: ComponentFixture<CourseUnenrollmentModalComponent>;
    let component: CourseUnenrollmentModalComponent;
    let courseService: CourseManagementService;
    let unenrollFromCourseStub: jest.SpyInstance;
    let alertService: AlertService;
    let successAlertStub: jest.SpyInstance;
    let errorAlertStub: jest.SpyInstance;
    let router: Router;
    let navigateStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CourseUnenrollmentModalComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [
                provideRouter([]),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseUnenrollmentModalComponent);
                component = fixture.componentInstance;
                component.course = new Course();
                component.course.id = 1;
                component.course.title = 'Unenrollment Test Course Title';
                courseService = TestBed.inject(CourseManagementService);
                unenrollFromCourseStub = jest.spyOn(courseService, 'unenrollFromCourse').mockReturnValue(of(new HttpResponse({ body: ['student-group-name'] })));
                alertService = TestBed.inject(AlertService);
                successAlertStub = jest.spyOn(alertService, 'success');
                errorAlertStub = jest.spyOn(alertService, 'error');
                router = TestBed.inject(Router);
                navigateStub = jest.spyOn(router, 'navigate').mockImplementation();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should disable unenrollment button when confirmation input is invalid', () => {
        const confirmationInput = fixture.nativeElement.querySelector('input');
        const unenrollButton = fixture.debugElement.query(By.css('#course-unenrollment-accept-button')).nativeElement;
        fixture.detectChanges();
        expect(unenrollButton.disabled).toBeTrue();
        const inputEvent = new Event('input');
        confirmationInput.value = 'Some invalid Title';
        confirmationInput.dispatchEvent(inputEvent);
        fixture.detectChanges();
        expect(unenrollButton.disabled).toBeTrue();
    });

    it('should enable unenrollment button when confirmation input is valid', () => {
        const confirmationInput = fixture.nativeElement.querySelector('input');
        const unenrollButton = fixture.debugElement.query(By.css('#course-unenrollment-accept-button')).nativeElement;
        const inputEvent = new Event('input');
        confirmationInput.value = 'Unenrollment Test Course Title';
        confirmationInput.dispatchEvent(inputEvent);
        fixture.detectChanges();
        expect(unenrollButton.disabled).toBeFalse();
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
        unenrollFromCourseStub = jest.spyOn(courseService, 'unenrollFromCourse').mockReturnValue(throwError(() => httpError));
        fixture.changeDetectorRef.detectChanges();
        component.onUnenroll();
        expect(unenrollFromCourseStub).toHaveBeenCalledOnce();
        expect(errorAlertStub).toHaveBeenCalledOnce();
    });

    it('should report student can enroll again for valid', () => {
        component.course.unenrollmentEnabled = true;
        component.course.enrollmentEnabled = true;
        component.course.enrollmentEndDate = dayjs().add(1, 'day');
        fixture.changeDetectorRef.detectChanges();
        expect(component.canEnrollAgain).toBeTrue();
    });

    it('should not report student can enroll again for invalid', () => {
        component.course.unenrollmentEnabled = true;
        component.course.enrollmentEnabled = true;
        component.course.enrollmentEndDate = dayjs().subtract(1, 'day');
        fixture.changeDetectorRef.detectChanges();
        expect(component.canEnrollAgain).toBeFalse();
    });
});
