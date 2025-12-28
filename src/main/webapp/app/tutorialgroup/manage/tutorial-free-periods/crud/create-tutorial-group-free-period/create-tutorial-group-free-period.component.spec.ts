import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { CreateTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import {
    formDataToTutorialGroupFreePeriodDTO,
    generateExampleTutorialGroupFreePeriod,
    tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData,
} from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupFreePeriodFormComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('CreateTutorialGroupFreePeriodComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CreateTutorialGroupFreePeriodComponent>;
    let component: CreateTutorialGroupFreePeriodComponent;
    let tutorialGroupFreePeriodService: TutorialGroupFreePeriodService;
    const course = { id: 1, timeZone: 'Europe/Berlin' } as Course;
    const configurationId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CreateTutorialGroupFreePeriodComponent, OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupFreePeriodService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CreateTutorialGroupFreePeriodComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('tutorialGroupConfigurationId', configurationId);
        fixture.componentRef.setInput('course', course);
        component.open();
        tutorialGroupFreePeriodService = TestBed.inject(TutorialGroupFreePeriodService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should send POST request upon form submission and close dialog', () => {
        const exampleFreePeriod = generateExampleTutorialGroupFreePeriod({});
        delete exampleFreePeriod.id;

        const createResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: exampleFreePeriod,
            status: 201,
        });

        const createStub = vi.spyOn(tutorialGroupFreePeriodService, 'create').mockReturnValue(of(createResponse));
        const freePeriodCreatedSpy = vi.spyOn(component.freePeriodCreated, 'emit');

        const sessionForm: TutorialGroupFreePeriodFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormComponent)).componentInstance;

        const formData = tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(exampleFreePeriod, 'Europe/Berlin');

        sessionForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(course.id!, configurationId, formDataToTutorialGroupFreePeriodDTO(formData));
        expect(freePeriodCreatedSpy).toHaveBeenCalledOnce();
        expect(component.dialogVisible()).toBe(false);
    });

    it('should throw an error when date and alternativeDate are undefined', () => {
        let undefinedDate: Date | undefined;
        const time = new Date('2023-12-31T12:00:00');
        expect(() => {
            CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(undefinedDate, time, undefinedDate);
        }).toThrow('date and time are undefined');
    });

    it('should correctly combine date and time for freePeriods+', () => {
        const startDate = new Date('2021-01-01');
        const endDate: Date | undefined = new Date('2021-01-07');
        const startTime: Date | undefined = undefined;
        const endTime: Date | undefined = undefined;
        const combinedStart = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        const combinedEnd = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        expect(combinedStart).toEqual(new Date('2021-01-01T00:00:00'));
        expect(combinedEnd).toEqual(new Date('2021-01-07T23:59:00'));
    });

    it('should correctly combine date and time for freeDay', () => {
        const startDate = new Date('2021-01-01');
        const endDate: Date | undefined = undefined;
        const startTime: Date | undefined = undefined;
        const endTime: Date | undefined = undefined;
        const combinedStart = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        const combinedEnd = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        expect(combinedStart).toEqual(new Date('2021-01-01T00:00:00'));
        expect(combinedEnd).toEqual(new Date('2021-01-01T23:59:00'));
    });

    it('should correctly combine date and time for freePeriodWithinDay', () => {
        const startDate = new Date('2021-01-01');
        const endDate: Date | undefined = undefined;
        const startTime: Date | undefined = new Date('2023-12-31T16:00:00');
        const endTime: Date | undefined = new Date('2023-12-31T18:00:00');
        const combinedStart = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        const combinedEnd = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        expect(combinedStart).toEqual(new Date('2021-01-01T16:00:00'));
        expect(combinedEnd).toEqual(new Date('2021-01-01T18:00:00'));
    });
});
