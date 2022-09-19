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
import { User } from 'app/core/user/user.model';
import { TutorialGroupFormStubComponent } from '../../../stubs/tutorial-group-form-stub.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';

describe('EditTutorialGroupComponent', () => {
    let editTutorialGroupComponentFixture: ComponentFixture<EditTutorialGroupComponent>;
    let editTutorialGroupComponent: EditTutorialGroupComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    let findCourseSpy: jest.SpyInstance;
    let courseService: CourseManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [EditTutorialGroupComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                simpleActivatedRouteProvider(new Map([['tutorialGroupId', 1]]), new Map([['courseId', 1]])),
            ],
        })
            .compileComponents()
            .then(() => {
                editTutorialGroupComponentFixture = TestBed.createComponent(EditTutorialGroupComponent);
                editTutorialGroupComponent = editTutorialGroupComponentFixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                findCourseSpy = jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editTutorialGroupComponentFixture.detectChanges();
        expect(editTutorialGroupComponent).not.toBeNull();
        expect(findCourseSpy).toHaveBeenCalledOnce();
        expect(findCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should set form data correctly', () => {
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);

        const tutorialGroupOfResponse = new TutorialGroup();
        tutorialGroupOfResponse.id = 1;
        tutorialGroupOfResponse.title = 'test';

        const response: HttpResponse<TutorialGroup> = new HttpResponse({
            body: tutorialGroupOfResponse,
            status: 200,
        });

        const findByIdStub = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));

        editTutorialGroupComponentFixture.detectChanges();

        const tutorialGroupFormStubComponent: TutorialGroupFormStubComponent = editTutorialGroupComponentFixture.debugElement.query(
            By.directive(TutorialGroupFormStubComponent),
        ).componentInstance;

        expect(editTutorialGroupComponent.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(findByIdStub).toHaveBeenCalledWith(1, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editTutorialGroupComponent.formData.title).toEqual(tutorialGroupOfResponse.title);
        expect(tutorialGroupFormStubComponent.formData).toEqual(editTutorialGroupComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);
        const exampleTeachingAssistant = new User();
        exampleTeachingAssistant.login = 'testLogin';

        const tutorialGroupInDatabase: TutorialGroup = new TutorialGroup();
        tutorialGroupInDatabase.id = 1;
        tutorialGroupInDatabase.title = 'test';
        tutorialGroupInDatabase.teachingAssistant = exampleTeachingAssistant;

        const findByIdResponse: HttpResponse<TutorialGroup> = new HttpResponse({
            body: tutorialGroupInDatabase,
            status: 200,
        });
        const findByIdStub = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(findByIdResponse));

        editTutorialGroupComponentFixture.detectChanges();
        expect(findByIdStub).toHaveBeenCalledWith(1, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(editTutorialGroupComponent.tutorialGroup).toEqual(tutorialGroupInDatabase);

        const changedTutorialGroup: TutorialGroup = {
            ...tutorialGroupInDatabase,
            title: 'Changed',
        };

        const updateResponse: HttpResponse<TutorialGroup> = new HttpResponse({
            body: changedTutorialGroup,
            status: 200,
        });

        const updatedStub = jest.spyOn(tutorialGroupService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const tutorialGroupForm: TutorialGroupFormStubComponent = editTutorialGroupComponentFixture.debugElement.query(
            By.directive(TutorialGroupFormStubComponent),
        ).componentInstance;

        tutorialGroupForm.formSubmitted.emit({
            title: changedTutorialGroup.title,
            teachingAssistant: exampleTeachingAssistant,
        });

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
        navigateSpy.mockRestore();
    });
});
function simpleActivatedRouteProvider(arg0: Map<string, number>, arg1: Map<string, number>): any {
    throw new Error('Function not implemented.');
}
