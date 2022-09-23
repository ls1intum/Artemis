import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { simpleTwoLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { Router } from '@angular/router';
import { EditTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupsConfigurationFormStubComponent } from '../../../stubs/tutorial-groups-configuration-form-sub.component';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { generateExampleTutorialGroupsConfiguration, tutorialsGroupsConfigurationToFormData } from './tutorialGroupsConfigurationExampleModels';

describe('EditTutorialGroupsConfigurationComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupsConfigurationComponent>;
    let component: EditTutorialGroupsConfigurationComponent;
    let configurationService: TutorialGroupsConfigurationService;
    let findConfigurationSpy: jest.SpyInstance;
    let exampleConfiguration: TutorialGroupsConfiguration;
    const courseId = 1;
    const configurationId = 2;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                EditTutorialGroupsConfigurationComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupsConfigurationFormStubComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                simpleTwoLayerActivatedRouteProvider(new Map([['tutorialGroupsConfigurationId', configurationId]]), new Map([['courseId', courseId]])),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupsConfigurationComponent);
                component = fixture.componentInstance;
                configurationService = TestBed.inject(TutorialGroupsConfigurationService);

                exampleConfiguration = generateExampleTutorialGroupsConfiguration();

                findConfigurationSpy = jest.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: exampleConfiguration })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(courseId, configurationId);
    });

    it('should set form data correctly', () => {
        fixture.detectChanges();

        const formStub: TutorialGroupsConfigurationFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormStubComponent)).componentInstance;

        expect(component.tutorialGroupsConfiguration).toEqual(exampleConfiguration);
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(courseId, configurationId);

        expect(component.formData.period).toEqual([exampleConfiguration.tutorialPeriodStartInclusive?.toDate(), exampleConfiguration.tutorialPeriodEndInclusive?.toDate()]);
        expect(component.formData.timeZone).toEqual(exampleConfiguration.timeZone);
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedConfiguration: TutorialGroupsConfiguration = {
            ...exampleConfiguration,
            timeZone: 'Europe/Belgrade',
        };

        const updateResponse: HttpResponse<TutorialGroupsConfiguration> = new HttpResponse({
            body: changedConfiguration,
            status: 200,
        });

        const updatedStub = jest.spyOn(configurationService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupsConfigurationFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormStubComponent)).componentInstance;

        const formData = tutorialsGroupsConfigurationToFormData(changedConfiguration);

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(courseId, configurationId, changedConfiguration, formData.period);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', courseId, 'tutorial-groups-management']);
    });
});
