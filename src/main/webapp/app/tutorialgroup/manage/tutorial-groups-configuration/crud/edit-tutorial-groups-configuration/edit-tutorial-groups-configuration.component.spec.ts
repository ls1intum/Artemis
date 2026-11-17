import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Subject, of } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { EditTutorialGroupsConfigurationComponent } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import {
    generateExampleTutorialGroupsConfigurationDTO,
    tutorialsGroupsConfigurationDtoToFormData,
} from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { TutorialGroupsConfigurationFormComponent } from '../tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { expectComponentRendered } from 'test/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';

describe('EditTutorialGroupsConfigurationComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupsConfigurationComponent>;
    let component: EditTutorialGroupsConfigurationComponent;
    let configurationService: TutorialGroupsConfigurationService;
    let courseStorageService: CourseStorageService;
    let findConfigurationSpy: ReturnType<typeof vi.spyOn>;
    let exampleConfiguration: TutorialGroupConfiguration;
    const course = { id: 1, title: 'Example' } as Course;
    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
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
        exampleConfiguration = generateExampleTutorialGroupsConfigurationDTO({ id: 2 });
        courseStorageService = TestBed.inject(CourseStorageService);
        findConfigurationSpy = vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(exampleConfiguration));
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
        expect(component.formData()).toEqual(tutorialsGroupsConfigurationDtoToFormData(exampleConfiguration));
        expect(formStub.formData()).toEqual(component.formData());
    });

    it('should send PUT request upon form submission and navigate', () => {
        const changedConfiguration: TutorialGroupConfiguration = {
            ...exampleConfiguration,
        };

        const updateCourseSpy = vi.spyOn(courseStorageService, 'updateCourse');
        const updatedStub = vi.spyOn(configurationService, 'update').mockReturnValue(of(changedConfiguration));
        const navigateSpy = vi.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupsConfigurationFormComponent = fixture.debugElement.query(By.directive(TutorialGroupsConfigurationFormComponent)).componentInstance;

        const formData = tutorialsGroupsConfigurationDtoToFormData(changedConfiguration);

        sessionForm.formSubmitted.emit(formData);
        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(course.id, exampleConfiguration.id, [], formData);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups']);
        expect(updateCourseSpy).toHaveBeenCalledOnce();
        expect(updateCourseSpy).toHaveBeenCalledWith(component.course());
    });
    it('keeps the form open on save failure and clears loading', () => {
        const request = new Subject<TutorialGroupConfiguration>();
        vi.spyOn(configurationService, 'update').mockReturnValue(request);
        const navigate = vi.spyOn(router, 'navigate');
        const updateCourse = vi.spyOn(courseStorageService, 'updateCourse');
        const alert = vi.spyOn(TestBed.inject(AlertService), 'error');

        component.updateTutorialGroupsConfiguration(tutorialsGroupsConfigurationDtoToFormData(generateExampleTutorialGroupsConfigurationDTO({})));
        expect(component.isLoading()).toBe(true);
        request.error(new HttpErrorResponse({ status: 403 }));
        expect(component.isLoading()).toBe(false);
        expect(navigate).not.toHaveBeenCalled();
        expect(updateCourse).not.toHaveBeenCalled();
        expect(alert).toHaveBeenCalledOnce();
    });

    it('cancels an unfinished save on destroy without navigating', () => {
        const request = new Subject<TutorialGroupConfiguration>();
        vi.spyOn(configurationService, 'update').mockReturnValue(request);
        const navigate = vi.spyOn(router, 'navigate');

        component.updateTutorialGroupsConfiguration(tutorialsGroupsConfigurationDtoToFormData(generateExampleTutorialGroupsConfigurationDTO({})));
        component.ngOnDestroy();
        request.next(generateExampleTutorialGroupsConfigurationDTO({}));
        expect(component.isLoading()).toBe(false);
        expect(request.observed).toBe(false);
        expect(navigate).not.toHaveBeenCalled();
    });

    it('preserves existing free periods in the update request', () => {
        const freePeriods = [{ id: 7, reason: 'Holiday' }];
        component.tutorialGroupsConfiguration.tutorialGroupFreePeriods = freePeriods;
        const update = vi.spyOn(configurationService, 'update').mockReturnValue(of(exampleConfiguration));
        const formData = tutorialsGroupsConfigurationDtoToFormData(exampleConfiguration);
        component.updateTutorialGroupsConfiguration(formData);
        expect(update).toHaveBeenCalledWith(course.id, exampleConfiguration.id, freePeriods, formData);
    });
});
