import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';

import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { TutorialGroupManagementResolve } from 'app/tutorialgroup/manage/service/tutorial-group-management-resolve.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';

describe('TutorialGroupManagementResolve', () => {
    setupTestBed({ zoneless: true });

    let resolver: TutorialGroupManagementResolve;
    let service: CourseManagementService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                TutorialGroupManagementResolve,
                { provide: Router, useClass: MockRouter },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                MockProvider(CourseManagementService),
            ],
        });
        resolver = TestBed.inject(TutorialGroupManagementResolve);
        service = TestBed.inject(CourseManagementService);
        router = TestBed.inject(Router);
    });

    it('should navigate to tutorial-groups-checklist if course has no tutorialGroupsConfiguration', () => {
        const course: Course = new Course();
        course.id = 1;
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(router, 'navigate');
        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
    });

    it('should navigate to tutorial-groups-checklist if course has no timeZone', () => {
        const course: Course = new Course();
        course.id = 1;
        course.tutorialGroupsConfiguration = { id: 1 };
        vi.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(router, 'navigate');
        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
    });

    it('should not navigate to tutorial-groups-checklist if state url matches edit configuration url', () => {
        const course: Course = new Course();
        course.id = 1;
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
