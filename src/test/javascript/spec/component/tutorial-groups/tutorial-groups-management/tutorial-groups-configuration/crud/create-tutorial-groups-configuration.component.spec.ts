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
import { CreateTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsConfigurationFormStubComponent } from '../../../stubs/tutorial-groups-configuration-form-sub.component';
import { generateExampleTutorialGroupsConfiguration, tutorialsGroupsConfigurationToFormData } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';

describe('CreateTutorialGroupsConfigurationComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupsConfigurationComponent>;
    let component: CreateTutorialGroupsConfigurationComponent;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let courseManagementService: CourseManagementService;
    const course = { id: 1, title: 'Example' };
    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CreateTutorialGroupsConfigurationComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupsConfigurationFormStubComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute({}, {}, { course }, {}),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CreateTutorialGroupsConfigurationComponent);
                component = fixture.componentInstance;
                tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
                courseManagementService = TestBed.inject(CourseManagementService);
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
        const exampleConfiguration = generateExampleTutorialGroupsConfiguration({});
        delete exampleConfiguration.id;

        const createResponse: HttpResponse<TutorialGroupsConfiguration> = new HttpResponse({
            body: exampleConfiguration,
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupsConfigurationService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');
        const updateCourseSpy = jest.spyOn(courseManagementService, 'courseWasUpdated');

        const sessionForm: TutorialGroupsConfigurationFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormStubComponent)).componentInstance;

        const formData = tutorialsGroupsConfigurationToFormData(exampleConfiguration);

        sessionForm.formSubmitted.emit(formData);

        // will be taken from period
        delete exampleConfiguration.tutorialPeriodStartInclusive;
        delete exampleConfiguration.tutorialPeriodEndInclusive;

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(exampleConfiguration, course.id, formData.period);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups-management']);
        expect(updateCourseSpy).toHaveBeenCalledOnce();
        expect(updateCourseSpy).toHaveBeenCalledWith(component.course);
    });
});
