import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { CourseRegistrationDetailComponent } from 'app/overview/course-registration/course-registration-detail/course-registration-detail.component';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs/internal/observable/of';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CoursePrerequisitesButtonComponent } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';
import { CourseRegistrationButtonComponent } from 'app/overview/course-registration/course-registration-button/course-registration-button.component';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { MockRouter } from '../../../helpers/mocks/mock-router';

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
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(TranslateService),
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        component.ngOnInit();
    });

    it('should parse the courseId from the route', () => {
        component.ngOnInit();
        expect(component.courseId).toBe(123);
    });

    it('should load the course using findOneToRegister', fakeAsync(() => {
        jest.spyOn(courseService, 'findOneToRegister').mockReturnValue(of(new HttpResponse<Course>({ body: course1 })));

        component.ngOnInit();

        expect(component.course).not.toBeNull();
        expect(component.course?.id).toBe(course1.id);
        expect(component.course?.title).toBe(course1.title);
    }));

    it('should redirect to the course page if the API endpoint redirected to /courses/:courseId/for-dashboard', async () => {
        const httpResponseComingFromForDashboardEndpoint = new HttpResponse({
            body: course1,
            headers: new HttpHeaders(),
            url: `/api/courses/${course1.id}/for-dashboard`,
        });
        jest.spyOn(courseService, 'findOneToRegister').mockReturnValue(of(httpResponseComingFromForDashboardEndpoint));

        await component.ngOnInit();

        expect(router.navigate).toHaveBeenCalledWith(['courses', course1.id]);
    });
});
