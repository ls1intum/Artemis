import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CreateTutorialGroupsConfigurationComponent } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { TutorialGroupsConfigurationFormStubComponent } from 'test/helpers/stubs/tutorialgroup/tutorial-groups-configuration-form-sub.component';
import { generateExampleTutorialGroupsConfiguration, tutorialsGroupsConfigurationToFormData } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ArtemisDatePipe } from '../../../../../shared/pipes/artemis-date.pipe';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TutorialGroupsConfigurationFormComponent } from '../tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CreateTutorialGroupsConfigurationComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CreateTutorialGroupsConfigurationComponent>;
    let component: CreateTutorialGroupsConfigurationComponent;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let courseManagementService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    const course = { id: 1, title: 'Example' };
    const router = new MockRouter();
    let getCourseSpy: ReturnType<typeof vi.spyOn>;

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
        getCourseSpy = vi.spyOn(courseManagementService, 'find').mockReturnValue(of(response));
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
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

        const createStub = vi.spyOn(tutorialGroupsConfigurationService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = vi.spyOn(router, 'navigate');
        const updateCourseSpy = vi.spyOn(courseStorageService, 'updateCourse');

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
