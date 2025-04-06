import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupDetailComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';

describe('CourseTutorialGroupDetailComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupDetailComponent>;
    let component: CourseTutorialGroupDetailComponent;
    let tutorialGroupService: TutorialGroupsService;
    let courseManagementService: CourseManagementService;
    let tutorialGroupOfResponse: TutorialGroup;
    let courseOfResponse: Course;
    let findStub: jest.SpyInstance;
    let findByIdStub: jest.SpyInstance;

    const parentParams = { courseId: 2 };
    const parentRoute = { parent: { params: of(parentParams) } } as any as ActivatedRoute;
    const route = { params: of({ tutorialGroupId: 1 }), parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseTutorialGroupDetailComponent);
                component = fixture.componentInstance;
                tutorialGroupService = TestBed.inject(TutorialGroupsService);
                courseManagementService = TestBed.inject(CourseManagementService);
                tutorialGroupOfResponse = generateExampleTutorialGroup({ id: 1 });
                const response: HttpResponse<TutorialGroup> = new HttpResponse({
                    body: tutorialGroupOfResponse,
                    status: 200,
                });
                courseOfResponse = { id: 2 } as Course;
                const courseResponse: HttpResponse<Course> = new HttpResponse({
                    body: courseOfResponse,
                });
                findStub = jest.spyOn(courseManagementService, 'find').mockReturnValue(of(courseResponse));
                findByIdStub = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should load tutorial group', () => {
        fixture.detectChanges();
        expect(component.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(component.course).toEqual(courseOfResponse);
        expect(findByIdStub).toHaveBeenCalledWith(2, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(findStub).toHaveBeenCalledWith(2);
        expect(findStub).toHaveBeenCalledOnce();
    });
});
