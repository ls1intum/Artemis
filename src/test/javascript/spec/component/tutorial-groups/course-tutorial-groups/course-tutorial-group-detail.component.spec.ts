import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupDetailComponent } from 'app/overview/tutorial-group-details/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { LoadingIndicatorContainerStubComponent } from '../../../helpers/stubs/loading-indicator-container-stub.component';
import { TutorialGroupDetailStubComponent } from '../stubs/tutorial-group-detail-stub.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { of } from 'rxjs';
import { mockedActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

describe('CourseTutorialGroupDetailComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupDetailComponent>;
    let component: CourseTutorialGroupDetailComponent;
    let tutorialGroupService: TutorialGroupsService;
    let courseManagementService: CourseManagementService;
    let tutorialGroupOfResponse: TutorialGroup;
    let courseOfResponse: Course;
    let findStub: jest.SpyInstance;
    let findByIdStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupDetailComponent, TutorialGroupDetailStubComponent, LoadingIndicatorContainerStubComponent],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                mockedActivatedRoute({
                    courseId: 2,
                    tutorialGroupId: 1,
                }),
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
