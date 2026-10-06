import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, HttpStatusCode, provideHttpClient } from '@angular/common/http';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import {
    ActivatedRouteSnapshot,
    Event,
    NavigationCancel,
    NavigationCancellationCode,
    NavigationError,
    RedirectCommand,
    Router,
    RouterStateSnapshot,
    provideRouter,
} from '@angular/router';
import { TutorialGroupManagementCourseResolver } from 'app/tutorialgroup/manage/service/tutorial-group-management-course-resolver.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Component({ template: '' })
class DummyComponent {}

describe('TutorialGroupManagementResolve', () => {
    let resolver: TutorialGroupManagementCourseResolver;
    let service: CourseManagementService;
    let configurationService: TutorialGroupsConfigurationService;
    let router: Router;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                TutorialGroupManagementCourseResolver,
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                MockProvider(TutorialGroupsConfigurationService),
            ],
        });
        resolver = TestBed.inject(TutorialGroupManagementCourseResolver);
        service = TestBed.inject(CourseManagementService);
        configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        router = TestBed.inject(Router);
        alertService = TestBed.inject(AlertService);
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse<TutorialGroupConfigurationDTO>({})));
    });

    /** Runs the resolver for course 1 and records what it emits and what it throws. */
    const resolveCourse = (state: Partial<RouterStateSnapshot> = {}) => {
        const next = vi.fn();
        const error = vi.fn();
        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, state as RouterStateSnapshot).subscribe({ next, error });
        return { next, error };
    };

    /** Asserts that the resolver redirected to the given URL, without emitting a course first. */
    const expectRedirectTo = ({ next, error }: ReturnType<typeof resolveCourse>, url: string) => {
        expect(next).not.toHaveBeenCalled();
        expect(error).toHaveBeenCalledOnce();
        const redirect = error.mock.calls[0][0];
        expect(redirect).toBeInstanceOf(RedirectCommand);
        expect(router.serializeUrl((redirect as RedirectCommand).redirectTo)).toBe(url);
    };

    it('should redirect instructors to tutorial-groups-checklist if course has no tutorialGroupsConfiguration', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = true;
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        expectRedirectTo(resolveCourse(), '/course-management/1/tutorial-groups-checklist');
    });

    it('should redirect instructors to tutorial-groups-checklist if course has no timeZone', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = true;
        course.tutorialGroupsConfiguration = { id: 1 };
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        expectRedirectTo(resolveCourse(), '/course-management/1/tutorial-groups-checklist');
    });

    it.each([
        { tutorialGroupsConfiguration: undefined, timeZone: 'Europe/Berlin' },
        { tutorialGroupsConfiguration: { id: 1 }, timeZone: undefined },
    ])('should warn tutors and redirect to the course overview if the tutorial group configuration is incomplete', ({ tutorialGroupsConfiguration, timeZone }) => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = false;
        course.tutorialGroupsConfiguration = tutorialGroupsConfiguration;
        course.timeZone = timeZone;
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(alertService, 'warning');

        const result = resolveCourse();

        expect(alertService.warning).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.configurationRequiredForTutor');
        expectRedirectTo(result, '/courses');
    });

    it('should allow tutors to access tutorial group management if the configuration is complete', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = false;
        course.tutorialGroupsConfiguration = { id: 1 };
        course.timeZone = 'Europe/Berlin';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(alertService, 'warning');

        const { next, error } = resolveCourse();

        expect(next).toHaveBeenCalledExactlyOnceWith(course);
        expect(error).not.toHaveBeenCalled();
        expect(alertService.warning).not.toHaveBeenCalled();
    });

    it('should not redirect if only the configuration endpoint knows the configuration', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = false;
        course.timeZone = 'Europe/Berlin';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse<TutorialGroupConfigurationDTO>({ body: { id: 5 } })));
        vi.spyOn(alertService, 'warning');

        const { next, error } = resolveCourse();

        expect(error).not.toHaveBeenCalled();
        expect(alertService.warning).not.toHaveBeenCalled();
        expect(next).toHaveBeenCalledOnce();
        expect((next.mock.calls[0][0] as Course).tutorialGroupsConfiguration?.id).toBe(5);
    });

    it('should show an error and redirect to the course overview if the user is not at least tutor in the course', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = false;
        course.timeZone = 'Europe/Berlin';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse<TutorialGroupConfigurationDTO>({ body: { id: 5 } })));
        vi.spyOn(alertService, 'error');

        const result = resolveCourse();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
        expectRedirectTo(result, '/courses');
    });

    it('should show an error and redirect to the course overview if the course request is forbidden', () => {
        vi.spyOn(service, 'find').mockReturnValue(throwError(() => new HttpErrorResponse({ status: HttpStatusCode.Forbidden })));
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse<TutorialGroupConfigurationDTO>({ body: { id: 5 } })));
        vi.spyOn(alertService, 'error');

        const result = resolveCourse();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
        expectRedirectTo(result, '/courses');
    });

    it('should show an error and redirect to the course overview if the configuration request is forbidden', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.timeZone = 'Europe/Berlin';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(throwError(() => new HttpErrorResponse({ status: HttpStatusCode.Forbidden })));
        vi.spyOn(alertService, 'error');

        const result = resolveCourse();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
        expectRedirectTo(result, '/courses');
    });

    it('should propagate errors other than forbidden without showing the authorization error', () => {
        const serverError = new HttpErrorResponse({ status: HttpStatusCode.InternalServerError });
        vi.spyOn(service, 'find').mockReturnValue(throwError(() => serverError));
        vi.spyOn(alertService, 'error');

        const { error } = resolveCourse();

        expect(error).toHaveBeenCalledExactlyOnceWith(serverError);
        expect(alertService.error).not.toHaveBeenCalled();
    });

    it('should not redirect to tutorial-groups-checklist if state url matches edit configuration url', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.tutorialGroupsConfiguration = { id: 2 };
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        const { next, error } = resolveCourse({ url: '/course-management/1/tutorial-groups/configuration/2/edit' });

        expect(next).toHaveBeenCalledExactlyOnceWith(course);
        expect(error).not.toHaveBeenCalled();
    });

    // The unit tests above check the redirect the resolver throws. This one checks that the router turns it into a
    // redirect of the running navigation: a cancellation with the Redirect code followed by the target, not an error.
    describe('inside a navigation', () => {
        beforeEach(() => {
            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [
                    provideRouter([
                        { path: 'course-management/:courseId/tutorial-groups', component: DummyComponent, resolve: { course: TutorialGroupManagementCourseResolver } },
                        { path: 'courses', component: DummyComponent },
                    ]),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                    { provide: TranslateService, useClass: MockTranslateService },
                    MockProvider(AlertService),
                    MockProvider(CourseManagementService, {
                        find: () => of(new HttpResponse({ body: Object.assign(new Course(), { id: 1, isAtLeastTutor: false }) })),
                    }),
                    MockProvider(TutorialGroupsConfigurationService, {
                        getOneOfCourse: () => of(new HttpResponse<TutorialGroupConfigurationDTO>({ body: { id: 5 } })),
                    }),
                ],
            });
            router = TestBed.inject(Router);
            alertService = TestBed.inject(AlertService);
        });

        it('cancels the navigation with a redirect to the course overview and keeps the alert', async () => {
            const events: Event[] = [];
            router.events.subscribe((event) => events.push(event));
            vi.spyOn(alertService, 'error');

            const navigated = await router.navigate(['/course-management', 1, 'tutorial-groups']);

            expect(navigated).toBe(true);
            expect(router.url).toBe('/courses');
            expect(alertService.error).toHaveBeenCalledExactlyOnceWith('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
            const cancel = events.find((event): event is NavigationCancel => event instanceof NavigationCancel);
            expect(cancel?.url).toBe('/course-management/1/tutorial-groups');
            expect(cancel?.code).toBe(NavigationCancellationCode.Redirect);
            expect(events.some((event) => event instanceof NavigationError)).toBe(false);
        });
    });
});
