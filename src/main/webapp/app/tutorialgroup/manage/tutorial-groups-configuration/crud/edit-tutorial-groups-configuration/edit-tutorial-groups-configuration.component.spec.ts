import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { EditTutorialGroupsConfigurationComponent } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { generateExampleTutorialGroupsConfiguration, tutorialsGroupsConfigurationToFormData } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ArtemisDatePipe } from '../../../../../shared/pipes/artemis-date.pipe';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TutorialGroupsConfigurationFormComponent } from '../tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { expectComponentRendered } from '../../../../../../../../test/javascript/spec/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';

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
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(AlertService),
                MockProvider(CourseStorageService),
                MockProvider(ArtemisDatePipe),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {
                        tutorialGroupsConfigurationId: 2,
                    },
                    {},
                    { course },
                    {},
                ),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(EditTutorialGroupsConfigurationComponent);
        component = fixture.componentInstance;
        configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        exampleConfiguration = generateExampleTutorialGroupsConfiguration({ id: 2 });
        course.tutorialGroupsConfiguration = exampleConfiguration;
        courseStorageService = TestBed.inject(CourseStorageService);
        findConfigurationSpy = jest.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: exampleConfiguration })));
        fixture.detectChanges();
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
        const formStub = expectComponentRendered<TutorialGroupsConfigurationFormComponent>(fixture, 'jhi-tutorial-groups-configuration-form');

        expect(component.tutorialGroupsConfiguration).toEqual(exampleConfiguration);
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(course.id);
        fixture.detectChanges();
        expect(component.formData.period).toEqual([exampleConfiguration.tutorialPeriodStartInclusive?.toDate(), exampleConfiguration.tutorialPeriodEndInclusive?.toDate()]);
        expect(formStub.formData()).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const changedConfiguration: TutorialGroupsConfiguration = Object.assign({}, exampleConfiguration);

        const updateResponse: HttpResponse<TutorialGroupsConfiguration> = new HttpResponse({
            body: changedConfiguration,
            status: 200,
        });

        const updateCourseSpy = jest.spyOn(courseStorageService, 'updateCourse');
        const updatedStub = jest.spyOn(configurationService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupsConfigurationFormComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormComponent)).componentInstance;

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
