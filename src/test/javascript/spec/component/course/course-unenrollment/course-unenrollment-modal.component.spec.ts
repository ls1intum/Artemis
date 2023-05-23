import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { CourseUnenrollmentModalComponent } from 'app/overview/course-unenrollment-modal.component';
import { Course } from 'app/entities/course.model';

describe('CourseRegistrationButtonComponent', () => {
    let fixture: ComponentFixture<CourseUnenrollmentModalComponent>;
    let component: CourseUnenrollmentModalComponent;
    let courseService: CourseManagementService;
    let unenrollFromCourseStub: jest.SpyInstance;
    let alertService: AlertService;
    let successAlertStub: jest.SpyInstance;
    let errorAlertStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseUnenrollmentModalComponent],
            providers: [MockProvider(CourseManagementService), MockProvider(AlertService)],
        })
            .overrideTemplate(CourseUnenrollmentModalComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseUnenrollmentModalComponent);
                component = fixture.componentInstance;
                component.course = new Course();
                component.course.id = 1;
                courseService = TestBed.inject(CourseManagementService);
                unenrollFromCourseStub = jest.spyOn(courseService, 'unenrollFromCourse').mockReturnValue(of(new HttpResponse({ body: new User() })));
                alertService = TestBed.inject(AlertService);
                successAlertStub = jest.spyOn(alertService, 'success');
                errorAlertStub = jest.spyOn(alertService, 'error');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should alert success after unenrollment', () => {
        component.onUnenroll();
        expect(unenrollFromCourseStub).toHaveBeenCalledOnce();
        expect(successAlertStub).toHaveBeenCalledOnce();
    });

    it('should alert error after unsucessfull unenrollment', () => {
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        unenrollFromCourseStub = jest.spyOn(courseService, 'unenrollFromCourse').mockReturnValue(throwError(() => httpError));
        component.onUnenroll();
        expect(unenrollFromCourseStub).toHaveBeenCalledOnce();
        expect(errorAlertStub).toHaveBeenCalledOnce();
    });

    it('should report student can enroll again for valid', () => {
        component.course.registrationEnabled = true;
        component.course.enrollmentEndDate = dayjs().add(1, 'day');
        expect(component.canEnrollAgain).toBeTrue();
    });

    it('should not report student can enroll again for invalid', () => {
        component.course.registrationEnabled = true;
        component.course.enrollmentEndDate = dayjs().subtract(1, 'day');
        expect(component.canEnrollAgain).toBeFalse();
    });
});
