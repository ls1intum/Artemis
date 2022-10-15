import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
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

describe('TutorialFreePeriodFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodFormComponent>;
    let component: TutorialGroupFreePeriodFormComponent;

    const validDate = new Date(Date.UTC(2021, 1, 1));
    const validReason = 'Holiday';

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            declarations: [TutorialGroupFreePeriodFormComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupFreePeriodFormComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();

                clickSubmit = generateClickSubmitButton(component, fixture, {
                    date: validDate,
                    reason: validReason,
                });

                testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should correctly set form values in edit mode', () => {
        component.isEditMode = true;
        const formData: TutorialGroupFreePeriodFormData = {
            date: validDate,
            reason: validReason,
        };
        fixture.detectChanges();

        component.formData = formData;
        component.ngOnChanges();

        const formControlNames = ['date', 'reason'];
        formControlNames.forEach((control) => {
            expect(component.form.get(control)!.value).toEqual(formData[control]);
        });
    });

    it('should submit valid form', fakeAsync(() => {
        setValidFormValues();
        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();
        clickSubmit(true);
    }));

    it('should block submit when required property is missing', fakeAsync(() => {
        const requiredControlNames = ['date'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    }));

    // === helper functions ===

    const setValidFormValues = () => {
        component.dateControl!.setValue(validDate);
        component.reasonControl!.setValue(validReason);
    };
});
