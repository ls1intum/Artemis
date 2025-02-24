import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { EditTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import {
    formDataToTutorialGroupFreePeriodDTO,
    generateExampleTutorialGroupFreePeriod,
    tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData,
} from '../../../helpers/tutorialGroupFreePeriodExampleModel';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { generateExampleTutorialGroupsConfiguration } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupFreePeriodFormComponent } from '../../../../../../../../main/webapp/app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

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
        const formStub: TutorialGroupFreePeriodFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormComponent)).componentInstance;
        expect(component.formData).toEqual(tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(examplePeriod, 'Europe/Berlin'));
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should set form data correctly for editing free periods', () => {
        const periodToEdit: TutorialGroupFreePeriod = generateExampleTutorialGroupFreePeriod({
            id: 2,
            start: dayjs('2021-01-02T00:00:00').tz('UTC'),
            end: dayjs('2021-01-07T23:59:00').tz('UTC'),
            reason: 'TestReason',
        });

        setUpTestComponent(periodToEdit);

        const formStub: TutorialGroupFreePeriodFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormComponent)).componentInstance;
        expect(component.formData).toEqual(tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(periodToEdit, 'Europe/Berlin'));
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should set form data correctly for editing free periods within a day', () => {
        const periodWithinDayToEdit: TutorialGroupFreePeriod = generateExampleTutorialGroupFreePeriod({
            id: 2,
            start: dayjs('2021-01-08T12:00:00').tz('UTC'),
            end: dayjs('2021-01-09T14:00:00').tz('UTC'),
            reason: 'TestReason',
        });
        setUpTestComponent(periodWithinDayToEdit);
        const formStub: TutorialGroupFreePeriodFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormComponent)).componentInstance;
        expect(component.formData).toEqual(tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(periodWithinDayToEdit, 'Europe/Berlin'));
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const changedPeriod: TutorialGroupFreePeriod = {
            ...examplePeriod,
            reason: 'Changed',
        };

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

    // Helper functions
    function setUpTestComponent(freePeriod: TutorialGroupFreePeriod) {
        fixture = TestBed.createComponent(EditTutorialGroupFreePeriodComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        periodService = TestBed.inject(TutorialGroupFreePeriodService);
        examplePeriod = freePeriod;
        exampleConfiguration = generateExampleTutorialGroupsConfiguration({});
        component.course = course;
        component.tutorialGroupFreePeriod = examplePeriod;
        component.tutorialGroupsConfiguration = exampleConfiguration;
        component.initialize();
        fixture.detectChanges();
    }
});
