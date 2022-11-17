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
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupFormStubComponent } from '../../../stubs/tutorial-group-form-stub.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { generateExampleTutorialGroup, tutorialGroupToTutorialGroupFormData } from '../../../helpers/tutorialGroupExampleModels';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';

describe('EditTutorialGroupComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupComponent>;
    let component: EditTutorialGroupComponent;
    let findTutorialGroupSpy: jest.SpyInstance;
    let tutorialGroupService: TutorialGroupsService;
    const course = { id: 2, title: 'Example' };

    let exampleTutorialGroup: TutorialGroup;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [EditTutorialGroupComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {
                        tutorialGroupId: 1,
                    },
                    {},
                    { course },
                    {},
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupComponent);
                component = fixture.componentInstance;
                exampleTutorialGroup = generateExampleTutorialGroup({});

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
        delete exampleTutorialGroup.isUserRegistered;
        delete exampleTutorialGroup.isUserTutor;
        delete exampleTutorialGroup.course;
        delete exampleTutorialGroup.numberOfRegisteredUsers;
        delete exampleTutorialGroup.courseTitle;
        delete exampleTutorialGroup.teachingAssistantName;

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
        expect(updatedStub).toHaveBeenCalledWith(2, 1, changedTutorialGroup, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 2, 'tutorial-groups']);
    });
});
