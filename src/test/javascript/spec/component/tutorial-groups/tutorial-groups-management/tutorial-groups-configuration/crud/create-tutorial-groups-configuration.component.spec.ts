import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CreateTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsConfigurationFormStubComponent } from '../../../stubs/tutorial-groups-configuration-form-sub.component';
import { generateExampleTutorialGroupsConfiguration, tutorialsGroupsConfigurationToFormData } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/entities/course.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ArtemisDatePipe } from '../../../../../../../../main/webapp/app/shared/pipes/artemis-date.pipe';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TutorialGroupsConfigurationFormComponent } from '../../../../../../../../main/webapp/app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CreateTutorialGroupsConfigurationComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupsConfigurationComponent>;
    let component: CreateTutorialGroupsConfigurationComponent;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let courseManagementService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    const course = { id: 1, title: 'Example' };
    const router = new MockRouter();
    let getCourseSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                MockProvider(ArtemisDatePipe),
                { provide: Router, useValue: router },
                mockedActivatedRoute({ courseId: course.id! }, {}, {}, {}),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CreateTutorialGroupsConfigurationComponent);
        component = fixture.componentInstance;
        tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
        courseManagementService = TestBed.inject(CourseManagementService);
        courseStorageService = TestBed.inject(CourseStorageService);
        const response: HttpResponse<Course> = new HttpResponse({
            body: course,
            status: 201,
        });
        getCourseSpy = jest.spyOn(courseManagementService, 'find').mockReturnValue(of(response));
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
        expect(getCourseSpy).toHaveBeenCalledWith(course.id!);
        expect(getCourseSpy).toHaveBeenCalledOnce();
    });

    it('should send POST request upon form submission and navigate', () => {
        const exampleConfiguration = generateExampleTutorialGroupsConfiguration({});
        delete exampleConfiguration.id;

        const createResponse: HttpResponse<TutorialGroupsConfiguration> = new HttpResponse({
            body: exampleConfiguration,
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupsConfigurationService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');
        const updateCourseSpy = jest.spyOn(courseStorageService, 'updateCourse');

        const sessionForm: TutorialGroupsConfigurationFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormComponent)).componentInstance;

        const formData = tutorialsGroupsConfigurationToFormData(exampleConfiguration);

        sessionForm.formSubmitted.emit(formData);

        // will be taken from period
        delete exampleConfiguration.tutorialPeriodStartInclusive;
        delete exampleConfiguration.tutorialPeriodEndInclusive;

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(exampleConfiguration, course.id, formData.period);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups-checklist']);
        expect(updateCourseSpy).toHaveBeenCalledOnce();
        expect(updateCourseSpy).toHaveBeenCalledWith(component.course);
    });
});
