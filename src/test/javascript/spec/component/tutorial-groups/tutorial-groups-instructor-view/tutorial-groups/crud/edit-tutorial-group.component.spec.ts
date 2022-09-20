import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupFormStubComponent } from '../../../stubs/tutorial-group-form-stub.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { simpleTwoLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { tutorialGroupToTutorialGroupFormData, generateExampleTutorialGroup } from './tutorialGroupExampleModels';

describe('EditTutorialGroupComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupComponent>;
    let component: EditTutorialGroupComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    let findCourseSpy: jest.SpyInstance;
    let courseService: CourseManagementService;
    let findTutorialGroupSpy: jest.SpyInstance;
    let tutorialGroupService: TutorialGroupsService;

    let exampleTutorialGroup: TutorialGroup;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [EditTutorialGroupComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                simpleTwoLayerActivatedRouteProvider(new Map([['tutorialGroupId', 1]]), new Map([['courseId', 2]])),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                findCourseSpy = jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

                exampleTutorialGroup = generateExampleTutorialGroup();

                tutorialGroupService = TestBed.inject(TutorialGroupsService);

                const response: HttpResponse<TutorialGroup> = new HttpResponse({
                    body: exampleTutorialGroup,
                    status: 200,
                });

                findTutorialGroupSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(findCourseSpy).toHaveBeenCalledOnce();
        expect(findCourseSpy).toHaveBeenCalledWith(2);
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
    });

    it('should set form data correctly', () => {
        fixture.detectChanges();

        const tutorialGroupFormStubComponent: TutorialGroupFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFormStubComponent)).componentInstance;

        expect(component.tutorialGroup).toEqual(exampleTutorialGroup);
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();

        expect(component.formData).toEqual(tutorialGroupToTutorialGroupFormData(exampleTutorialGroup));
        expect(tutorialGroupFormStubComponent.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedTutorialGroup = {
            ...exampleTutorialGroup,
            title: 'Changed',
        } as TutorialGroup;

        const updateResponse: HttpResponse<TutorialGroup> = new HttpResponse({
            body: changedTutorialGroup,
            status: 200,
        });

        const updatedStub = jest.spyOn(tutorialGroupService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const tutorialGroupForm: TutorialGroupFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFormStubComponent)).componentInstance;

        const formData = tutorialGroupToTutorialGroupFormData(changedTutorialGroup);

        tutorialGroupForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(2, 1, changedTutorialGroup);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 2, 'tutorial-groups-management']);
    });
});
