import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-group-form/tutorial-group-form.component';
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/create-tutorial-group/create-tutorial-group.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../helpers/stubs/loading-indicator-container-stub.component';

@Component({ selector: 'jhi-tutorial-group-form', template: '' })
class TutorialGroupFormStubComponent {
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();
}

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

        const formDate: TutorialGroupFormData = {
            title: 'Test',
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
        tutorialGroupForm.formSubmitted.emit(formDate);

        createTutorialGroupComponentFixture.whenStable().then(() => {
            const tutorialGroupCallArgument: TutorialGroup = createStub.mock.calls[0][0];

            expect(tutorialGroupCallArgument.title).toEqual(formDate.title);

            expect(createStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();

            navigateSpy.mockRestore();
        });
    }));
});
