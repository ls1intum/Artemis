import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import dayjs from 'dayjs/esm';
import { EditTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodFormStubComponent } from '../../../stubs/tutorial-group-free-period-form-stub.component';
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
            declarations: [EditTutorialGroupFreePeriodComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFreePeriodFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TutorialGroupFreePeriodService), MockProvider(AlertService), MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                setUpTestComponent(generateExampleTutorialGroupFreePeriod({}));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should set form data correctly for editing free days', () => {
        const formStub: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;
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

        const formStub: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;
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
        const formStub: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;
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

        const sessionForm: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;

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
