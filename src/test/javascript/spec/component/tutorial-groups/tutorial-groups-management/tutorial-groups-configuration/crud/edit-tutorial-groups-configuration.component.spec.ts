import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { Router } from '@angular/router';
import { EditTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupsConfigurationFormStubComponent } from '../../../stubs/tutorial-groups-configuration-form-sub.component';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { generateExampleTutorialGroupsConfiguration, tutorialsGroupsConfigurationToFormData } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/entities/course.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

describe('EditTutorialGroupsConfigurationComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupsConfigurationComponent>;
    let component: EditTutorialGroupsConfigurationComponent;
    let configurationService: TutorialGroupsConfigurationService;
    let courseStorageService: CourseStorageService;
    let findConfigurationSpy: jest.SpyInstance;
    let exampleConfiguration: TutorialGroupsConfiguration;
    const course = { id: 1, title: 'Example' } as Course;
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
                MockProvider(CourseStorageService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {
                        tutorialGroupsConfigurationId: 2,
                    },
                    {},
                    { course },
                    {},
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupsConfigurationComponent);
                component = fixture.componentInstance;
                configurationService = TestBed.inject(TutorialGroupsConfigurationService);

                exampleConfiguration = generateExampleTutorialGroupsConfiguration({ id: 2 });
                course.tutorialGroupsConfiguration = exampleConfiguration;
                courseStorageService = TestBed.inject(CourseStorageService);

                findConfigurationSpy = jest.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: exampleConfiguration })));
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(course.id);
    });

    it('should set form data correctly', () => {
        const formStub: TutorialGroupsConfigurationFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormStubComponent)).componentInstance;

        expect(component.tutorialGroupsConfiguration).toEqual(exampleConfiguration);
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(course.id);

        expect(component.formData.period).toEqual([exampleConfiguration.tutorialPeriodStartInclusive?.toDate(), exampleConfiguration.tutorialPeriodEndInclusive?.toDate()]);
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const changedConfiguration: TutorialGroupsConfiguration = {
            ...exampleConfiguration,
        };

        const updateResponse: HttpResponse<TutorialGroupsConfiguration> = new HttpResponse({
            body: changedConfiguration,
            status: 200,
        });

        const updateCourseSpy = jest.spyOn(courseStorageService, 'updateCourse');
        const updatedStub = jest.spyOn(configurationService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupsConfigurationFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormStubComponent)).componentInstance;

        const formData = tutorialsGroupsConfigurationToFormData(changedConfiguration);

        sessionForm.formSubmitted.emit(formData);
        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(course.id, exampleConfiguration.id, changedConfiguration, formData.period);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups']);
        expect(updateCourseSpy).toHaveBeenCalledOnce();
        expect(updateCourseSpy).toHaveBeenCalledWith(component.course);
    });
});
