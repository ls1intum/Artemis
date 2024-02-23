import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import '@angular/localize/init';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import {
    TimeFrame,
    TutorialGroupFreePeriodFormComponent,
    TutorialGroupFreePeriodFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from '../../../helpers/tutorialGroupFormsUtils';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { runOnPushChangeDetection } from '../../../../../helpers/on-push-change-detection.helper';
import dayjs from 'dayjs/esm';

describe('TutorialFreePeriodFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodFormComponent>;
    let component: TutorialGroupFreePeriodFormComponent;

    const validStartDateBerlin = dayjs('2021-01-01T00:00:00').tz('Europe/Berlin').toDate();
    const validEndDateBerlinFreePeriod = dayjs('2021-01-07T00:00:00').tz('Europe/Berlin').toDate();
    const validStartTimeBerlin = dayjs('2021-01-01T10:00:00').tz('Europe/Berlin').toDate();
    const validEndTimeBerlin = dayjs('2021-01-01T12:00:00').tz('Europe/Berlin').toDate();

    const validEndDateUTCFreeDay = new Date(Date.UTC(2021, 1, 1));
    const validEndDateBerlinFreeDay = new Date(validEndDateUTCFreeDay.toLocaleString('de-DE', { timeZone: 'Europe/Berlin' }));
    validEndDateBerlinFreeDay.setHours(23, 59, 59);

    const validReason = 'Holiday';

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            declarations: [TutorialGroupFreePeriodFormComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockPipe(ArtemisDatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupFreePeriodFormComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();

                clickSubmit = generateClickSubmitButton(component, fixture, {
                    startDate: validStartDateBerlin,
                    endDate: undefined,
                    startTime: undefined,
                    endTime: undefined,
                    reason: validReason,
                });

                testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should correctly set timeFrame in edit mode for freeDay', () => {
        const formData: TutorialGroupFreePeriodFormData = {
            startDate: validStartDateBerlin,
            endDate: undefined,
            startTime: undefined,
            endTime: undefined,
            reason: validReason,
        };
        timeFrameTestHelperMethod(TimeFrame.Day, formData);
    });

    it('should correctly set form values and timeFrame in edit mode for freePeriod', () => {
        const formData: TutorialGroupFreePeriodFormData = {
            startDate: validStartDateBerlin,
            endDate: validEndDateBerlinFreePeriod,
            startTime: undefined,
            endTime: undefined,
            reason: validReason,
        };
        timeFrameTestHelperMethod(TimeFrame.Period, formData);
    });

    it('should correctly set form values and timeFrame in edit mode for freePeriodWithinDay', () => {
        const formData: TutorialGroupFreePeriodFormData = {
            startDate: validStartDateBerlin,
            endDate: undefined,
            startTime: validEndTimeBerlin,
            endTime: validEndTimeBerlin,
            reason: validReason,
        };
        timeFrameTestHelperMethod(TimeFrame.PeriodWithinDay, formData);
    });

    it('should submit valid form', fakeAsync(() => {
        setFormValues(validStartDateBerlin, undefined, undefined, undefined, validReason);
        runOnPushChangeDetection(fixture);
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();
        clickSubmit(true);
    }));

    it('should block submit when required property is missing', fakeAsync(() => {
        const requiredControlNames = ['startDate'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    }));

    it('should reset unused form values when time frame changes', () => {
        setFormValues(validStartDateBerlin, validEndDateBerlinFreePeriod, undefined, undefined, validReason);
        setTimeFrameAndCheckValues(TimeFrame.Day, validStartDateBerlin, undefined, undefined, undefined);
        setFormValues(validStartDateBerlin, validEndDateBerlinFreePeriod, undefined, undefined, validReason);
        setTimeFrameAndCheckValues(TimeFrame.PeriodWithinDay, validStartDateBerlin, undefined, undefined, undefined);

        setFormValues(validStartDateBerlin, undefined, validStartTimeBerlin, validEndTimeBerlin, validReason);
        setTimeFrameAndCheckValues(TimeFrame.Period, validStartDateBerlin, undefined, undefined, undefined);
        setFormValues(validStartDateBerlin, undefined, validStartTimeBerlin, validEndTimeBerlin, validReason);
        setTimeFrameAndCheckValues(TimeFrame.Day, validStartDateBerlin, undefined, undefined, undefined);
    });

    const testCases = [
        {
            description: 'should set timeFrame initial correctly when editing an existing free period',
            expectedTimeFrame: TimeFrame.Period,
            formData: {
                startDate: validStartDateBerlin,
                endDate: validEndDateBerlinFreePeriod,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
        },
        {
            description: 'should set timeFrame initial correctly when editing an existing free day',
            expectedTimeFrame: TimeFrame.Day,
            formData: {
                startDate: validStartDateBerlin,
                endDate: undefined,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
        },
        {
            description: 'should set timeFrame initial correctly when editing an existing free period within day',
            expectedTimeFrame: TimeFrame.PeriodWithinDay,
            formData: {
                startDate: validStartDateBerlin,
                endDate: undefined,
                startTime: validStartTimeBerlin,
                endTime: validEndTimeBerlin,
                reason: validReason,
            },
        },
    ];

    test.each(testCases)('%s', ({ expectedTimeFrame, formData }) => {
        component.isEditMode = true;
        component.formData = formData;
        component.ngOnChanges();
        expect(component.timeFrameControl).toBe(expectedTimeFrame);
    });

    it('should allow submit when time frame is day and form is valid', () => {
        testIsSubmitPossibleHelperMethod(
            TimeFrame.Day,
            {
                startDate: validStartDateBerlin,
                endDate: undefined,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
            true,
        );
    });

    it('should not allow submit when time frame is day and form is invalid', () => {
        testIsSubmitPossibleHelperMethod(
            TimeFrame.Day,
            {
                startDate: undefined,
                endDate: undefined,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
            false,
        );
    });

    it('should allow submit when time frame is Period and form is valid', () => {
        testIsSubmitPossibleHelperMethod(
            TimeFrame.Period,
            {
                startDate: validStartDateBerlin,
                endDate: validEndDateBerlinFreePeriod,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
            true,
        );
    });

    it('should not allow submit when time frame is Period and form is invalid', () => {
        // missing start date
        testIsSubmitPossibleHelperMethod(
            TimeFrame.Period,
            {
                startDate: undefined,
                endDate: validEndDateBerlinFreeDay,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
            false,
        );

        // missing end date
        testIsSubmitPossibleHelperMethod(
            TimeFrame.Period,
            {
                startDate: validStartDateBerlin,
                endDate: undefined,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
            false,
        );

        // missing start and end date
        testIsSubmitPossibleHelperMethod(
            TimeFrame.Period,
            {
                startDate: undefined,
                endDate: undefined,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
            false,
        );

        // end date before start date
        testIsSubmitPossibleHelperMethod(
            TimeFrame.Period,
            {
                startDate: validEndDateBerlinFreeDay,
                endDate: validStartDateBerlin,
                startTime: undefined,
                endTime: undefined,
                reason: validReason,
            },
            false,
        );
    });

    it('should allow submit when time frame is PeriodWithinDay and form is valid', () => {
        testIsSubmitPossibleHelperMethod(
            TimeFrame.PeriodWithinDay,
            {
                startDate: validStartDateBerlin,
                endDate: undefined,
                startTime: validStartTimeBerlin,
                endTime: validEndTimeBerlin,
                reason: validReason,
            },
            true,
        );
    });

    it('should not allow submit when time frame is PeriodWithinDay and form is invalid', () => {
        // missing start date
        testIsSubmitPossibleHelperMethod(
            TimeFrame.PeriodWithinDay,
            {
                startDate: undefined,
                endDate: undefined,
                startTime: validStartTimeBerlin,
                endTime: validEndTimeBerlin,
                reason: validReason,
            },
            false,
        );

        // missing start time
        testIsSubmitPossibleHelperMethod(
            TimeFrame.PeriodWithinDay,
            {
                startDate: validStartDateBerlin,
                endDate: undefined,
                startTime: undefined,
                endTime: validEndTimeBerlin,
                reason: validReason,
            },
            false,
        );

        // missing end time
        testIsSubmitPossibleHelperMethod(
            TimeFrame.PeriodWithinDay,
            {
                startDate: validStartDateBerlin,
                endDate: undefined,
                startTime: validStartTimeBerlin,
                endTime: undefined,
                reason: validReason,
            },
            false,
        );

        // end time before start time
        testIsSubmitPossibleHelperMethod(
            TimeFrame.PeriodWithinDay,
            {
                startDate: validEndDateBerlinFreeDay,
                endDate: undefined,
                startTime: validEndTimeBerlin,
                endTime: validStartTimeBerlin,
                reason: validReason,
            },
            false,
        );
    });

    // === helper functions ===
    const setFormValues = (startDate: Date | undefined, endDate: Date | undefined, startTime: Date | undefined, endTime: Date | undefined, reason: string) => {
        component.startDateControl!.setValue(startDate);
        component.endDateControl!.setValue(endDate);
        component.startTimeControl!.setValue(startTime);
        component.endTimeControl!.setValue(endTime);
        component.reasonControl!.setValue(reason);
    };

    const setValidFormValues = () => {
        component.startDateControl!.setValue(validStartDateBerlin);
        component.endDateControl!.setValue(undefined);
        component.startTimeControl!.setValue(undefined);
        component.endTimeControl!.setValue(undefined);
        component.reasonControl!.setValue(validReason);
    };

    const setTimeFrameAndCheckValues = (timeFrame: TimeFrame, startDate: Date | undefined, endDate: Date | undefined, startTime: Date | undefined, endTime: Date | undefined) => {
        component.setTimeFrame(timeFrame);
        if (startDate) {
            expect(component.form.get('startDate')!.value).toBe(startDate);
        } else {
            expect(component.form.get('startDate')!.value).toBeFalsy();
        }

        if (endDate) {
            expect(component.form.get('endDate')!.value).toBe(endDate);
        } else {
            expect(component.form.get('endDate')!.value).toBeFalsy();
        }

        if (startTime) {
            expect(component.form.get('startTime')!.value).toBe(startTime);
        } else {
            expect(component.form.get('startTime')!.value).toBeFalsy();
        }

        if (endTime) {
            expect(component.form.get('endTime')!.value).toBe(endTime);
        } else {
            expect(component.form.get('endTime')!.value).toBeFalsy();
        }
    };

    function timeFrameTestHelperMethod(expectedTimeFrame: TimeFrame, formData: TutorialGroupFreePeriodFormData): void {
        component.isEditMode = true;
        component.formData = formData;
        component.ngOnChanges();
        expect(component.timeFrameControl).toBe(expectedTimeFrame);
    }

    function testIsSubmitPossibleHelperMethod(selectedTimeFrame: TimeFrame, formData: TutorialGroupFreePeriodFormData, submitShouldBePossible: boolean): void {
        component.form.patchValue(formData);
        component.setTimeFrame(selectedTimeFrame);
        expect(component.isSubmitPossible).toBe(submitShouldBePossible);
    }
});
