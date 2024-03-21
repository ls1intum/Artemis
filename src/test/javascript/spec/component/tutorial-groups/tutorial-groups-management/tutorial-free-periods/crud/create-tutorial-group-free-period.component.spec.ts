import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { CreateTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodFormStubComponent } from '../../../stubs/tutorial-group-free-period-form-stub.component';
import {
    formDataToTutorialGroupFreePeriodDTO,
    generateExampleTutorialGroupFreePeriod,
    tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData,
} from '../../../helpers/tutorialGroupFreePeriodExampleModel';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

describe('CreateTutorialGroupFreePeriodComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupFreePeriodComponent>;
    let component: CreateTutorialGroupFreePeriodComponent;
    let tutorialGroupFreePeriodService: TutorialGroupFreePeriodService;
    const course = { id: 1, timeZone: 'Europe/Berlin' } as Course;
    const configurationId = 1;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CreateTutorialGroupFreePeriodComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupFreePeriodFormStubComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(TutorialGroupFreePeriodService), MockProvider(AlertService), MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                activeModal = TestBed.inject(NgbActiveModal);
                fixture = TestBed.createComponent(CreateTutorialGroupFreePeriodComponent);
                component = fixture.componentInstance;
                component.tutorialGroupConfigurationId = configurationId;
                component.course = course;
                component.initialize();
                tutorialGroupFreePeriodService = TestBed.inject(TutorialGroupFreePeriodService);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should send POST request upon form submission and close modal', () => {
        const exampleFreePeriod = generateExampleTutorialGroupFreePeriod({});
        delete exampleFreePeriod.id;

        const createResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: exampleFreePeriod,
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupFreePeriodService, 'create').mockReturnValue(of(createResponse));
        const closeSpy = jest.spyOn(activeModal, 'close');

        const sessionForm: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;

        const formData = tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData(exampleFreePeriod, 'Europe/Berlin');

        sessionForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(course.id!, configurationId, formDataToTutorialGroupFreePeriodDTO(formData));
        expect(closeSpy).toHaveBeenCalledOnce();
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
