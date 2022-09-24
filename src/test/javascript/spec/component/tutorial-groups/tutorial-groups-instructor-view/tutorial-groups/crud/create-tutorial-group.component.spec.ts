// tslint:disable:max-line-length
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { TutorialGroupFormStubComponent } from '../../../stubs/tutorial-group-form-stub.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { simpleTwoLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { generateExampleTutorialGroup, tutorialGroupToTutorialGroupFormData } from '../../../helpers/tutorialGroupExampleModels';

describe('CreateTutorialGroupComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupComponent>;
    let component: CreateTutorialGroupComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    let findCourseSpy: jest.SpyInstance;
    let courseService: CourseManagementService;
    let tutorialGroupService: TutorialGroupsService;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CreateTutorialGroupComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                MockProvider(CourseManagementService),
                { provide: Router, useValue: router },
                simpleTwoLayerActivatedRouteProvider(new Map(), new Map([['courseId', course.id]])),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CreateTutorialGroupComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                findCourseSpy = jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
                tutorialGroupService = TestBed.inject(TutorialGroupsService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(findCourseSpy).toHaveBeenCalledOnce();
        expect(findCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should send POST request upon form submission and navigate', () => {
        fixture.detectChanges();
        const exampleTutorialGroup = generateExampleTutorialGroup();
        delete exampleTutorialGroup.id;
        delete exampleTutorialGroup.tutorialGroupSchedule!.id;

        const createResponse: HttpResponse<TutorialGroup> = new HttpResponse({
            body: exampleTutorialGroup,
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const tutorialGroupForm: TutorialGroupFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFormStubComponent)).componentInstance;

        const formData = tutorialGroupToTutorialGroupFormData(exampleTutorialGroup);

        tutorialGroupForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(exampleTutorialGroup, course.id);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups-management']);
    });
});
