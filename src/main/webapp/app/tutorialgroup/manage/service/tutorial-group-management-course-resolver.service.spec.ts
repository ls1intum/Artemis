import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, HttpStatusCode, provideHttpClient } from '@angular/common/http';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { TutorialGroupManagementCourseResolver } from 'app/tutorialgroup/manage/service/tutorial-group-management-course-resolver.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AlertService } from 'app/foundation/service/alert.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

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
                { provide: Router, useClass: MockRouter },
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

    it('should navigate instructors to tutorial-groups-checklist if course has no tutorialGroupsConfiguration', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = true;
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(router, 'navigate');
        const next = vi.fn();

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe({ next });

        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
        expect(next).not.toHaveBeenCalled();
    });

    it('should navigate instructors to tutorial-groups-checklist if course has no timeZone', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = true;
        course.tutorialGroupsConfiguration = { id: 1 };
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(router, 'navigate');
        const next = vi.fn();

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe({ next });

        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
        expect(next).not.toHaveBeenCalled();
    });

    it.each([
        { tutorialGroupsConfiguration: undefined, timeZone: 'Europe/Berlin' },
        { tutorialGroupsConfiguration: { id: 1 }, timeZone: undefined },
    ])('should warn tutors and navigate to the course overview if the tutorial group configuration is incomplete', ({ tutorialGroupsConfiguration, timeZone }) => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = false;
        course.tutorialGroupsConfiguration = tutorialGroupsConfiguration;
        course.timeZone = timeZone;
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(router, 'navigate');
        vi.spyOn(alertService, 'warning');
        const next = vi.fn();

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe({ next });

        expect(alertService.warning).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.configurationRequiredForTutor');
        expect(router.navigate).toHaveBeenCalledWith(['/courses']);
        expect(router.navigate).not.toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
        expect(next).not.toHaveBeenCalled();
    });

    it('should allow tutors to access tutorial group management if the configuration is complete', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.isAtLeastInstructor = false;
        course.tutorialGroupsConfiguration = { id: 1 };
        course.timeZone = 'Europe/Berlin';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(router, 'navigate');
        vi.spyOn(alertService, 'warning');

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe();

        expect(router.navigate).not.toHaveBeenCalled();
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
        vi.spyOn(router, 'navigate');
        vi.spyOn(alertService, 'warning');
        let resolvedCourse: Course | undefined;

        resolver
            .resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot)
            .subscribe((course: Course) => (resolvedCourse = course));

        expect(router.navigate).not.toHaveBeenCalled();
        expect(alertService.warning).not.toHaveBeenCalled();
        expect(resolvedCourse?.tutorialGroupsConfiguration?.id).toBe(5);
    });

    it('should show an error and navigate to the course overview if the user is not at least tutor in the course', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = false;
        course.timeZone = 'Europe/Berlin';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse<TutorialGroupConfigurationDTO>({ body: { id: 5 } })));
        vi.spyOn(router, 'navigate');
        vi.spyOn(alertService, 'error');
        const next = vi.fn();

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe({ next });

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
        expect(router.navigate).toHaveBeenCalledWith(['/courses']);
        expect(router.navigate).not.toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
        expect(next).not.toHaveBeenCalled();
    });

    it('should show an error and navigate to the course overview if the course request is forbidden', () => {
        vi.spyOn(service, 'find').mockReturnValue(throwError(() => new HttpErrorResponse({ status: HttpStatusCode.Forbidden })));
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse<TutorialGroupConfigurationDTO>({ body: { id: 5 } })));
        vi.spyOn(router, 'navigate');
        vi.spyOn(alertService, 'error');
        const next = vi.fn();

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe({ next });

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
        expect(router.navigate).toHaveBeenCalledWith(['/courses']);
        expect(next).not.toHaveBeenCalled();
    });

    it('should show an error and navigate to the course overview if the configuration request is forbidden', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.timeZone = 'Europe/Berlin';
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(throwError(() => new HttpErrorResponse({ status: HttpStatusCode.Forbidden })));
        vi.spyOn(router, 'navigate');
        vi.spyOn(alertService, 'error');
        const next = vi.fn();

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe({ next });

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupsManagement.notAuthorized');
        expect(router.navigate).toHaveBeenCalledWith(['/courses']);
        expect(next).not.toHaveBeenCalled();
    });

    it('should propagate errors other than forbidden without showing the authorization error', () => {
        const serverError = new HttpErrorResponse({ status: HttpStatusCode.InternalServerError });
        vi.spyOn(service, 'find').mockReturnValue(throwError(() => serverError));
        vi.spyOn(router, 'navigate');
        vi.spyOn(alertService, 'error');
        const error = vi.fn();

        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe({ error });

        expect(error).toHaveBeenCalledWith(serverError);
        expect(alertService.error).not.toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not navigate to tutorial-groups-checklist if state url matches edit configuration url', () => {
        const course: Course = new Course();
        course.id = 1;
        course.isAtLeastTutor = true;
        course.tutorialGroupsConfiguration = { id: 2 };
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(router, 'navigate');
        resolver
            .resolve(
                { params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot,
                {
                    url: '/course-management/1/tutorial-groups/configuration/2/edit',
                } as unknown as RouterStateSnapshot,
            )
            .subscribe();
        expect(router.navigate).not.toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
    });
});
