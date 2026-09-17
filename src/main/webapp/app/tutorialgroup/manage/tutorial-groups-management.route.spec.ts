import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';

import { courseManagementRoutes } from 'app/course/manage/course-management.route';
import { tutorialGroupManagementRoutes } from 'app/tutorialgroup/manage/tutorial-groups-management.route';
import { TutorialGroupManagementCourseResolver } from 'app/tutorialgroup/manage/service/tutorial-group-management-course-resolver.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Authority, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { User } from 'app/account/user/user.model';
import { Course } from 'app/course/shared/entities/course.model';
import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Component({ template: '' })
class DummyComponent {}

describe('tutorialGroupManagementRoutes', () => {
    describe('shipped route configuration', () => {
        const containerRoute = courseManagementRoutes.find((route) => route.path === '' && !!route.children?.length);
        const tutorialGroupsRoute = containerRoute?.children?.find((route) => route.path === ':courseId/tutorial-groups');
        const managementRoute = tutorialGroupManagementRoutes.find((route) => route.path === '');

        it('declares the course resolver on the parent route', () => {
            expect(tutorialGroupsRoute?.resolve?.['course']).toBe(TutorialGroupManagementCourseResolver);
        });

        it('guards the management route with the global tutor authority', () => {
            expect(managementRoute?.data?.['authorities']).toBe(IS_AT_LEAST_TUTOR);
            expect(managementRoute?.canActivate).toContain(UserRouteAccessService);
        });
    });

    // The guard checks the *global* authority, the resolver checks the course-specific role. Angular runs every
    // canActivate before any resolver, so the two rejections apply to different users and neither replaces the other.
    describe('guard and resolver ordering', () => {
        let router: Router;
        let resolver: TutorialGroupManagementCourseResolver;
        let courseManagementService: CourseManagementService;

        const course = { id: 1, isAtLeastTutor: true, timeZone: 'Europe/Berlin' } as Course;

        const configureWith = (authorities: Authority[]) => {
            TestBed.configureTestingModule({
                providers: [
                    provideRouter([
                        {
                            path: 'course-management/:courseId/tutorial-groups',
                            resolve: { course: TutorialGroupManagementCourseResolver },
                            children: [
                                {
                                    path: '',
                                    component: DummyComponent,
                                    data: { authorities: IS_AT_LEAST_TUTOR },
                                    canActivate: [UserRouteAccessService],
                                },
                            ],
                        },
                        { path: '**', component: DummyComponent },
                    ]),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                    { provide: TranslateService, useClass: MockTranslateService },
                    MockProvider(AlertService),
                    MockProvider(SessionStorageService),
                    MockProvider(CourseManagementService, {
                        find: () => of(new HttpResponse({ body: course })),
                    }),
                    MockProvider(TutorialGroupsConfigurationService, {
                        getOneOfCourse: () => of(new HttpResponse<TutorialGroupConfigurationDTO>({ body: { id: 5 } })),
                    }),
                    MockProvider(AccountService, {
                        identity: () => Promise.resolve({ id: 1, login: 'student', authorities } as User),
                        hasAnyAuthority: (required: readonly Authority[]) => Promise.resolve(required.some((authority) => authorities.includes(authority))),
                    }),
                ],
            });
            router = TestBed.inject(Router);
            resolver = TestBed.inject(TutorialGroupManagementCourseResolver);
            courseManagementService = TestBed.inject(CourseManagementService);
        };

        beforeEach(() => {
            TestBed.resetTestingModule();
        });

        it('rejects a student-only account before the resolver runs', async () => {
            configureWith([Authority.STUDENT]);
            const resolveSpy = vi.spyOn(resolver, 'resolve');
            const findSpy = vi.spyOn(courseManagementService, 'find');

            const activated = await router.navigate(['/course-management', 1, 'tutorial-groups']);

            expect(activated).toBe(false);
            expect(resolveSpy).not.toHaveBeenCalled();
            expect(findSpy).not.toHaveBeenCalled();
        });

        it('lets an account with the global tutor authority reach the resolver', async () => {
            configureWith([Authority.STUDENT, Authority.TUTOR]);
            const resolveSpy = vi.spyOn(resolver, 'resolve');

            await router.navigate(['/course-management', 1, 'tutorial-groups']);

            expect(resolveSpy).toHaveBeenCalledOnce();
        });
    });
});
