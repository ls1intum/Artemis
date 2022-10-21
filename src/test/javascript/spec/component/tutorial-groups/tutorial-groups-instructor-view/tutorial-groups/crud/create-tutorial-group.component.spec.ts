// tslint:disable:max-line-length
import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { Language } from 'app/entities/course.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { TutorialGroupFormStubComponent } from '../../../stubs/tutorial-group-form-stub.component';

describe('CreateTutorialGroupComponent', () => {
    let createTutorialGroupComponentFixture: ComponentFixture<CreateTutorialGroupComponent>;
    let createTutorialGroupComponent: CreateTutorialGroupComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CreateTutorialGroupComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
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
            ],
        })
            .compileComponents()
            .then(() => {
                createTutorialGroupComponentFixture = TestBed.createComponent(CreateTutorialGroupComponent);
                createTutorialGroupComponent = createTutorialGroupComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        createTutorialGroupComponentFixture.detectChanges();
        expect(createTutorialGroupComponent).not.toBeNull();
    });

    it('should send POST request upon form submission and navigate', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);
        const exampleTeachingAssistant = new User();
        exampleTeachingAssistant.login = 'testLogin';

        const formData: TutorialGroupFormData = {
            title: 'Test',
            teachingAssistant: exampleTeachingAssistant,
            language: Language.GERMAN,
            additionalInformation: 'Test Info',
            capacity: 1,
            isOnline: true,
            campus: 'Garching',
        };

        const response: HttpResponse<TutorialGroup> = new HttpResponse({
            body: new TutorialGroup(),
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createTutorialGroupComponentFixture.detectChanges();
        tick();
        const tutorialGroupForm: TutorialGroupFormStubComponent = createTutorialGroupComponentFixture.debugElement.query(
            By.directive(TutorialGroupFormStubComponent),
        ).componentInstance;
        tutorialGroupForm.formSubmitted.emit(formData);

        createTutorialGroupComponentFixture.whenStable().then(() => {
            const tutorialGroupCallArgument: TutorialGroup = createStub.mock.calls[0][0];

            expect(tutorialGroupCallArgument.title).toEqual(formData.title);
            expect(tutorialGroupCallArgument.teachingAssistant).toEqual(formData.teachingAssistant);
            expect(tutorialGroupCallArgument.language).toEqual(formData.language);
            expect(tutorialGroupCallArgument.additionalInformation).toEqual(formData.additionalInformation);
            expect(tutorialGroupCallArgument.capacity).toEqual(formData.capacity);
            expect(tutorialGroupCallArgument.isOnline).toEqual(formData.isOnline);
            expect(tutorialGroupCallArgument.campus).toEqual(formData.campus);

            expect(createStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
            navigateSpy.mockRestore();
        });
    }));
});
