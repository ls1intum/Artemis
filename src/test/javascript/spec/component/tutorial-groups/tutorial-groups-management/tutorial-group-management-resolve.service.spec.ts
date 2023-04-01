import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { TutorialGroupManagementResolve } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-management-resolve.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from '../../../helpers/mocks/mock-router';

describe('TutorialGroupManagementResolve', () => {
    let resolver: TutorialGroupManagementResolve;
    let service: CourseManagementService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, HttpClientTestingModule],
            providers: [
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
        jest.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(router, 'navigate');
        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
    });

    it('should navigate to tutorial-groups-checklist if course has no timeZone', () => {
        const course: Course = new Course();
        course.id = 1;
        course.tutorialGroupsConfiguration = { id: 1 };
        jest.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(router, 'navigate');
        resolver.resolve({ params: { courseId: 1 } } as unknown as ActivatedRouteSnapshot, {} as unknown as RouterStateSnapshot).subscribe();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups-checklist']);
    });

    it('should not navigate to tutorial-groups-checklist if state url matches edit configuration url', () => {
        const course: Course = new Course();
        course.id = 1;
        course.tutorialGroupsConfiguration = { id: 2 };
        jest.spyOn(service, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(router, 'navigate');
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
