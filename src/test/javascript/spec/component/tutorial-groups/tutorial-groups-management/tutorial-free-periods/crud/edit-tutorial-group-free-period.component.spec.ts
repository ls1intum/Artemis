import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';

import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import {
    formDataToTutorialGroupFreePeriodDTO,
    generateExampleTutorialGroupFreePeriod,
    tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData,
} from '../../../helpers/tutorialGroupFreePeriodExampleModel';
import { generateExampleTutorialGroupsConfiguration } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupFreePeriodFormStubComponent } from '../../../stubs/tutorial-group-free-period-form-stub.component';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { EditTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { Course } from 'app/entities/course.model';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
                fixture = TestBed.createComponent(EditTutorialGroupFreePeriodComponent);
                component = fixture.componentInstance;
                activeModal = TestBed.inject(NgbActiveModal);
                periodService = TestBed.inject(TutorialGroupFreePeriodService);
                examplePeriod = generateExampleTutorialGroupFreePeriod({});
                exampleConfiguration = generateExampleTutorialGroupsConfiguration({});
                component.course = course;
                component.tutorialGroupFreePeriod = examplePeriod;
                component.tutorialGroupsConfiguration = exampleConfiguration;
                component.initialize();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should set form data correctly', () => {
        const formStub: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;
        expect(component.formData).toEqual(tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(examplePeriod, 'Europe/Berlin'));
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
            date: dayjs('2021-01-01T00:00:00').tz('Europe/Berlin').toDate(),
            reason: 'Changed',
        };

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(course.id!, exampleConfiguration.id!, examplePeriod.id!, formDataToTutorialGroupFreePeriodDTO(formData));
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
