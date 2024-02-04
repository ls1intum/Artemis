import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import '@angular/localize/init';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import {
    TutorialGroupFreePeriodFormComponent,
    TutorialGroupFreePeriodFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from '../../../helpers/tutorialGroupFormsUtils';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { runOnPushChangeDetection } from '../../../../../helpers/on-push-change-detection.helper';

describe('TutorialFreePeriodFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodFormComponent>;
    let component: TutorialGroupFreePeriodFormComponent;

    const validStartDateUTC = new Date(Date.UTC(2021, 1, 1));
    const validStartDateBerlin = new Date(validStartDateUTC.toLocaleString('de-DE', { timeZone: 'Europe/Berlin' }));
    validStartDateBerlin.setHours(0, 0);

    const validEndDateUTCFreeDay = new Date(Date.UTC(2021, 1, 1));
    const validEndDateBerlinFreeDay = new Date(validEndDateUTCFreeDay.toLocaleString('de-DE', { timeZone: 'Europe/Berlin' }));
    validEndDateBerlinFreeDay.setHours(23, 59, 59);

    // const validStartTime = new Date(Date.UTC(2021, 1, 1, 0, 0, 0));
    // const validEndTime = new Date(Date.UTC(2021, 1, 1, 23, 59, 59));

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

    it('should correctly set form values in edit mode for freeDay', () => {
        component.isEditMode = true;
        const formData: TutorialGroupFreePeriodFormData = {
            startDate: validStartDateBerlin,
            endDate: validEndDateBerlinFreeDay,
            startTime: undefined,
            endTime: undefined,
            reason: validReason,
        };
        component.formData = formData;
        component.ngOnChanges();

        // For a Free Day, the end date should be undefined
        formData.endDate = undefined;
        const formControlNames = ['startDate', 'endDate', 'startTime', 'endTime', 'reason'];
        formControlNames.forEach((control) => {
            const formValue = component.form.get(control)!.value;
            const expectedValue = formData[control];
            if (formValue === null && expectedValue === undefined) {
                expect(formValue).toBeNull();
            } else {
                expect(formValue).toEqual(expectedValue);
            }
        });
    });

    it('should submit valid form', fakeAsync(() => {
        setValidFormValues();
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

    // === helper functions ===

    const setValidFormValues = () => {
        component.startDateControl!.setValue(validStartDateBerlin);
        component.endDateControl!.setValue(undefined);
        component.startTimeControl!.setValue(undefined);
        component.endTimeControl!.setValue(undefined);
        component.reasonControl!.setValue(validReason);
    };
});
