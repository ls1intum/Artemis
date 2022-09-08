import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { LoadingIndicatorContainerStubComponent } from '../../../helpers/stubs/loading-indicator-container-stub.component';
import { User } from 'app/core/user/user.model';

@Component({ selector: 'jhi-tutorial-group-form', template: '' })
class TutorialGroupFormStubComponent {
    @Input() courseId: number;
    @Input() isEditMode = false;
    @Input() formData: TutorialGroupFormData;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();
}
describe('EditTutorialGroupComponent', () => {
    let editTutorialGroupComponentFixture: ComponentFixture<EditTutorialGroupComponent>;
    let editTutorialGroupComponent: EditTutorialGroupComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [EditTutorialGroupComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'tutorialGroupId':
                                        return 1;
                                }
                            },
                        }),
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'courseId':
                                                return 1;
                                        }
                                    },
                                }),
                            },
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                editTutorialGroupComponentFixture = TestBed.createComponent(EditTutorialGroupComponent);
                editTutorialGroupComponent = editTutorialGroupComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editTutorialGroupComponentFixture.detectChanges();
        expect(editTutorialGroupComponent).not.toBeNull();
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
        const tutorialGroupFormStubComponent: TutorialGroupFormStubComponent = editTutorialGroupComponentFixture.debugElement.query(
            By.directive(TutorialGroupFormStubComponent),
        ).componentInstance;

        editTutorialGroupComponentFixture.detectChanges();
        expect(editTutorialGroupComponent.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(findByIdStub).toHaveBeenCalledWith(1);
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
        expect(findByIdStub).toHaveBeenCalledWith(1);
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
