import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import '@angular/localize/init';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import {
    TutorialGroupsConfigurationFormData,
    TutorialGroupsConfigurationFormComponent,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from '../../../helpers/tutorialGroupFormsUtils';

describe('TutorialGroupsConfigurationFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsConfigurationFormComponent>;
    let component: TutorialGroupsConfigurationFormComponent;

    const validTimeZone = 'Europe/Berlin';
    const validPeriodStart = new Date(Date.UTC(2021, 1, 1));
    const validPeriodEnd = new Date(Date.UTC(2021, 2, 1));
    const validPeriod = [validPeriodStart, validPeriodEnd];

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTypeaheadModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            declarations: [TutorialGroupsConfigurationFormComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                (Intl as any).supportedValuesOf = () => [validTimeZone];
                fixture = TestBed.createComponent(TutorialGroupsConfigurationFormComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();

                clickSubmit = generateClickSubmitButton(component, fixture, {
                    timeZone: validTimeZone,
                    period: validPeriod,
                });

                testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        (Intl as any).supportedValuesOf = undefined;
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should correctly set form values in edit mode', () => {
        component.isEditMode = true;
        const formData: TutorialGroupsConfigurationFormData = {
            timeZone: validTimeZone,
            period: validPeriod,
        };
        fixture.detectChanges();

        component.formData = formData;
        component.ngOnChanges();

        const formControlNames = ['period', 'timeZone'];
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
        const requiredControlNames = ['timeZone', 'period'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    }));

    // === helper functions ===
    const setValidFormValues = () => {
        component.form.get('timeZone')!.setValue(validTimeZone);
        component.form.get('period')!.setValue(validPeriod);
    };
});
