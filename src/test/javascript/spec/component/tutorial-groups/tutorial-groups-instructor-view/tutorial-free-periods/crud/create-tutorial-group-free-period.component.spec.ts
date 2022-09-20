// tslint:disable:max-line-length
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { simpleTwoLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { CreateTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodFormStubComponent } from '../../../stubs/tutorial-group-free-period-form-stub.component';
import {
    formDataToTutorialGroupFreePeriodDTO,
    generateExampleTutorialGroupFreePeriod,
    tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData,
} from './tutorialGroupFreePeriodExampleModel';

describe('CreateTutorialGroupFreePeriodComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupFreePeriodComponent>;
    let component: CreateTutorialGroupFreePeriodComponent;
    let tutorialGroupFreePeriodService: TutorialGroupFreePeriodService;
    const courseId = 2;
    const configurationId = 1;
    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CreateTutorialGroupFreePeriodComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupFreePeriodFormStubComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(TutorialGroupFreePeriodService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                simpleTwoLayerActivatedRouteProvider(new Map([['tutorialGroupsConfigurationId', configurationId]]), new Map([['courseId', courseId]])),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CreateTutorialGroupFreePeriodComponent);
                component = fixture.componentInstance;
                tutorialGroupFreePeriodService = TestBed.inject(TutorialGroupFreePeriodService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should send POST request upon form submission and navigate', () => {
        fixture.detectChanges();
        const exampleFreePeriod = generateExampleTutorialGroupFreePeriod();
        delete exampleFreePeriod.id;

        const createResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: exampleFreePeriod,
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupFreePeriodService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;

        const formData = tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(exampleFreePeriod, 'Europe/Berlin');

        sessionForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(courseId, configurationId, formDataToTutorialGroupFreePeriodDTO(formData));
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', courseId, 'tutorial-groups-management', 'configuration', configurationId, 'tutorial-free-days']);
    });
});
