import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { EditTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import {
    formDataToTutorialGroupFreePeriodDTO,
    generateExampleTutorialGroupFreePeriod,
    tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData,
} from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupFreePeriodFormComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { expectComponentRendered } from '../../../../../../../../test/javascript/spec/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';

describe('EditTutorialGroupFreePeriodComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupFreePeriodComponent>;
    let component: EditTutorialGroupFreePeriodComponent;
    let periodService: TutorialGroupFreePeriodService;
    let examplePeriod: TutorialGroupFreePeriod;
    let exampleConfiguration: TutorialGroupsConfiguration;
    const course = {
        id: 1,
        timeZone: 'Europe/Berlin',
    } as Course;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupFreePeriodService),
                MockProvider(AlertService),
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        setUpTestComponent(generateExampleTutorialGroupFreePeriod({}));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should set form data correctly for editing free days', () => {
        const formStub = expectComponentRendered<TutorialGroupFreePeriodFormComponent>(fixture, 'jhi-tutorial-free-period-form');
        expect(component.formData).toEqual(tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(examplePeriod, 'Europe/Berlin'));
        expect(formStub.formData()).toEqual(component.formData);
    });

    it('should set form data correctly for editing free periods', () => {
        const periodToEdit: TutorialGroupFreePeriod = generateExampleTutorialGroupFreePeriod({
            id: 2,
            start: dayjs('2021-01-02T00:00:00').tz('UTC'),
            end: dayjs('2021-01-07T23:59:00').tz('UTC'),
            reason: 'TestReason',
        });

        setUpTestComponent(periodToEdit);
        const formStub = expectComponentRendered<TutorialGroupFreePeriodFormComponent>(fixture, 'jhi-tutorial-free-period-form');
        expect(component.formData).toEqual(tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(periodToEdit, 'Europe/Berlin'));
        expect(formStub.formData()).toEqual(component.formData);
    });

    it('should set form data correctly for editing free periods within a day', () => {
        const periodWithinDayToEdit: TutorialGroupFreePeriod = generateExampleTutorialGroupFreePeriod({
            id: 2,
            start: dayjs('2021-01-08T12:00:00').tz('UTC'),
            end: dayjs('2021-01-09T14:00:00').tz('UTC'),
            reason: 'TestReason',
        });
        setUpTestComponent(periodWithinDayToEdit);
        const formStub = expectComponentRendered<TutorialGroupFreePeriodFormComponent>(fixture, 'jhi-tutorial-free-period-form');
        expect(component.formData).toEqual(tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(periodWithinDayToEdit, 'Europe/Berlin'));
        expect(formStub.formData()).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const changedPeriod: TutorialGroupFreePeriod = Object.assign({}, examplePeriod, { reason: 'Changed' });

        const updateResponse: HttpResponse<TutorialGroupFreePeriod> = new HttpResponse({
            body: changedPeriod,
            status: 200,
        });

        const closeSpy = jest.spyOn(activeModal, 'close');
        const updatedStub = jest.spyOn(periodService, 'update').mockReturnValue(of(updateResponse));

        const sessionForm: TutorialGroupFreePeriodFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormComponent)).componentInstance;

        const formData = {
            startDate: dayjs('2021-01-01T00:00:00').tz('Europe/Berlin').toDate(),
            reason: 'Changed',
        };

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(course.id!, exampleConfiguration.id!, examplePeriod.id!, formDataToTutorialGroupFreePeriodDTO(formData));
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should not initialize if inputs are missing', () => {
        // Do not set any inputs
        fixture = TestBed.createComponent(EditTutorialGroupFreePeriodComponent);
        component = fixture.componentInstance;
        component.initialize();
        expect(component.isInitialized).toBeFalse();
    });

    it('should set startTime and endTime correctly when freePeriodWithinDay', () => {
        // Prepare a period within day
        const start = dayjs('2021-01-08T12:00:00').tz('UTC');
        const end = dayjs('2021-01-08T15:00:00').tz('UTC');
        const periodWithinDay: TutorialGroupFreePeriod = { id: 1, start, end, reason: 'Reason' } as any;

        // Stub static checks
        jest.spyOn(TutorialGroupFreePeriodsManagementComponent, 'isFreePeriod').mockReturnValue(false);
        jest.spyOn(TutorialGroupFreePeriodsManagementComponent, 'isFreePeriodWithinDay').mockReturnValue(true);

        // Set inputs and initialize
        fixture = TestBed.createComponent(EditTutorialGroupFreePeriodComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupFreePeriod', periodWithinDay);
        fixture.componentRef.setInput('tutorialGroupsConfiguration', { id: 1 } as TutorialGroupsConfiguration);
        component.initialize();

        // Expect formData startTime and endTime hours match the UTC→Berlin hour
        const berlinStartHour = start.tz('Europe/Berlin').hour();
        const berlinEndHour = end.tz('Europe/Berlin').hour();
        expect(component.formData.startTime!.getHours()).toBe(berlinStartHour);
        expect(component.formData.endTime!.getHours()).toBe(berlinEndHour);
        expect(component.isInitialized).toBeTrue();
    });

    // Helper functions
    function setUpTestComponent(freePeriod: TutorialGroupFreePeriod) {
        fixture = TestBed.createComponent(EditTutorialGroupFreePeriodComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        periodService = TestBed.inject(TutorialGroupFreePeriodService);
        examplePeriod = freePeriod;
        exampleConfiguration = generateExampleTutorialGroupsConfiguration({});
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupFreePeriod', examplePeriod);
        fixture.componentRef.setInput('tutorialGroupsConfiguration', exampleConfiguration);
        component.initialize();
        fixture.detectChanges();
    }
});
