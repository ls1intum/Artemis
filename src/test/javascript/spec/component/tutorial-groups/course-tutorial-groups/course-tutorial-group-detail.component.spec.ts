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
describe('CourseTutorialGroupDetailComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupDetailComponent>;
    let component: CourseTutorialGroupDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupDetailComponent, TutorialGroupDetailStubComponent, LoadingIndicatorContainerStubComponent],
            providers: [
                MockProvider(TutorialGroupsService),
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
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);

        const tutorialGroupOfResponse = generateExampleTutorialGroup({ id: 1 });

        const response: HttpResponse<TutorialGroup> = new HttpResponse({
            body: tutorialGroupOfResponse,
            status: 200,
        });

        const findByIdStub = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
        fixture.detectChanges();
        expect(component.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(component.tutorialGroupId).toBe(1);
        expect(component.courseId).toBe(2);
        expect(findByIdStub).toHaveBeenCalledWith(2, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
    });

    it('should navigate to course overview when course clicked callback called', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        fixture.detectChanges();
        component.onCourseClicked();
        expect(navigateSpy).toHaveBeenCalledWith(['/courses', 2]);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
