import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { CourseRegistrationDetailComponent } from 'app/core/course/overview/course-registration/course-registration-detail/course-registration-detail.component';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs/internal/observable/of';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CoursePrerequisitesButtonComponent } from 'app/core/course/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';
import { CourseRegistrationButtonComponent } from 'app/core/course/overview/course-registration/course-registration-button/course-registration-button.component';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { firstValueFrom, throwError } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseRegistrationDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseRegistrationDetailComponent>;
    let component: CourseRegistrationDetailComponent;
    let courseService: CourseManagementService;
    let router: MockRouter;

    const route = { params: of({ courseId: '123' }) } as any as ActivatedRoute;

    const course1 = {
        id: 123,
        title: 'Course A',
    };

    beforeEach(async () => {
        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [CourseRegistrationDetailComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AccountService),
                MockProvider(CourseManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).overrideComponent(CourseRegistrationDetailComponent, {
            remove: { imports: [CoursePrerequisitesButtonComponent, CourseRegistrationButtonComponent] },
            add: { imports: [MockComponent(CoursePrerequisitesButtonComponent), MockComponent(CourseRegistrationButtonComponent)] },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseRegistrationDetailComponent);
        component = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);

        // by default, assume that the course is not fully accessible but only available for registration
        vi.spyOn(courseService, 'findOneForRegistration').mockReturnValue(of(new HttpResponse<Course>({ body: course1 })));
        vi.spyOn(courseService, 'findOneForDashboard').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should parse the courseId from the route', () => {
        component.ngOnInit();
        expect(component.courseId).toBe(123);
    });

    it('should load the course using findOneForRegistration', async () => {
        component.ngOnInit();
        await fixture.whenStable();

        expect(component.course).toBeDefined();
        expect(component.course?.id).toBe(course1.id);
        expect(component.course?.title).toBe(course1.title);
    });

    it('should have a function isCourseFullyAccessible that returns true if the for-dashboard endpoint returns a 200', async () => {
        const httpResponseComingFromForDashboardEndpoint = new HttpResponse({
            body: course1,
            headers: new HttpHeaders(),
        });
        vi.spyOn(courseService, 'findOneForDashboard').mockReturnValue(of(httpResponseComingFromForDashboardEndpoint));

        component.ngOnInit();
        await fixture.whenStable();

        const result = await firstValueFrom(component.isCourseFullyAccessible());
        expect(result).toBe(true);
    });

    it('should have a function isCourseFullyAccessible that returns false if the for-dashboard endpoint returns a 403', async () => {
        const httpResponseComingFromForDashboardEndpoint = new HttpErrorResponse({
            headers: new HttpHeaders(),
            status: 403,
        });
        vi.spyOn(courseService, 'findOneForDashboard').mockReturnValue(throwError(() => httpResponseComingFromForDashboardEndpoint));

        component.ngOnInit();
        await fixture.whenStable();

        const result = await firstValueFrom(component.isCourseFullyAccessible());
        expect(result).toBe(false);
    });

    it('should redirect to the course page if the dashboard version is fully accessible', async () => {
        vi.spyOn(component, 'isCourseFullyAccessible').mockReturnValue(of(true));

        component.courseId = course1.id;
        component.redirectIfCourseIsFullyAccessible();
        await fixture.whenStable();

        expect(router.navigate).toHaveBeenCalledWith(['courses', course1.id]);
    });

    it('should not redirect to the course page if the dashboard version is not fully accessible', async () => {
        vi.spyOn(component, 'isCourseFullyAccessible').mockReturnValue(of(false));

        component.courseId = course1.id;
        component.redirectIfCourseIsFullyAccessible();
        await fixture.whenStable();

        expect(router.navigate).not.toHaveBeenCalled();
    });
});
