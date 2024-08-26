import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { CourseRegistrationDetailComponent } from 'app/overview/course-registration/course-registration-detail/course-registration-detail.component';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs/internal/observable/of';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CoursePrerequisitesButtonComponent } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';
import { CourseRegistrationButtonComponent } from 'app/overview/course-registration/course-registration-button/course-registration-button.component';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { lastValueFrom, throwError } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';

describe('CourseRegistrationDetailComponent', () => {
    let fixture: ComponentFixture<CourseRegistrationDetailComponent>;
    let component: CourseRegistrationDetailComponent;
    let courseService: CourseManagementService;
    let router: MockRouter;

    const parentRoute = { params: of({ courseId: '123' }) } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    const course1 = {
        id: 123,
        title: 'Course A',
    };

    beforeEach(() => {
        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseRegistrationDetailComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CoursePrerequisitesButtonComponent),
                MockComponent(CourseRegistrationButtonComponent),
            ],
            providers: [
                MockProvider(AccountService),
                MockProvider(CourseManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseRegistrationDetailComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);

                // by default, assume that the course is not fully accessible but only available for registration
                jest.spyOn(courseService, 'findOneForRegistration').mockReturnValue(of(new HttpResponse<Course>({ body: course1 })));
                jest.spyOn(courseService, 'findOneForDashboard').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should parse the courseId from the route', () => {
        component.ngOnInit();
        expect(component.courseId).toBe(123);
    });

    it('should load the course using findOneForRegistration', fakeAsync(() => {
        component.ngOnInit();

        expect(component.course).toBeDefined();
        expect(component.course?.id).toBe(course1.id);
        expect(component.course?.title).toBe(course1.title);
    }));

    it('should have a function isCourseFullyAccessible that returns true if the for-dashboard endpoint returns a 200', fakeAsync(() => {
        const httpResponseComingFromForDashboardEndpoint = new HttpResponse({
            body: course1,
            headers: new HttpHeaders(),
        });
        jest.spyOn(courseService, 'findOneForDashboard').mockReturnValue(of(httpResponseComingFromForDashboardEndpoint));

        component.ngOnInit();
        tick();

        const observable = component.isCourseFullyAccessible();
        return expect(lastValueFrom(observable)).resolves.toBeTrue();
    }));

    it('should have a function isCourseFullyAccessible that returns false if the for-dashboard endpoint returns a 403', fakeAsync(() => {
        const httpResponseComingFromForDashboardEndpoint = new HttpErrorResponse({
            headers: new HttpHeaders(),
            status: 403,
        });
        jest.spyOn(courseService, 'findOneForDashboard').mockReturnValue(throwError(httpResponseComingFromForDashboardEndpoint));

        component.ngOnInit();
        tick();

        const observable = component.isCourseFullyAccessible();
        return expect(lastValueFrom(observable)).resolves.toBeFalse();
    }));

    it('should redirect to the course page if the dashboard version is fully accessible', fakeAsync(() => {
        jest.spyOn(component, 'isCourseFullyAccessible').mockReturnValue(of(true));

        component.courseId = course1.id;
        component.redirectIfCourseIsFullyAccessible();

        expect(router.navigate).toHaveBeenCalledWith(['courses', course1.id]);
    }));

    it('should not redirect to the course page if the dashboard version is not fully accessible', fakeAsync(() => {
        jest.spyOn(component, 'isCourseFullyAccessible').mockReturnValue(of(false));

        component.courseId = course1.id;
        component.redirectIfCourseIsFullyAccessible();

        expect(router.navigate).not.toHaveBeenCalled();
    }));
});
