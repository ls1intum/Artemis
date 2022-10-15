// util methods for testing tutorial group forms as they all follow the same patterns

import {
    TutorialGroupFreePeriodFormComponent,
    TutorialGroupFreePeriodFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import {
    TutorialGroupSessionFormComponent,
    TutorialGroupSessionFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { ComponentFixture } from '@angular/core/testing';
import {
    TutorialGroupsConfigurationFormComponent,
    TutorialGroupsConfigurationFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import {
    TutorialGroupFormComponent,
    TutorialGroupFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';

type SupportedForms = TutorialGroupFreePeriodFormComponent | TutorialGroupSessionFormComponent | TutorialGroupsConfigurationFormComponent | TutorialGroupFormComponent;
type SupportedFixtures = ComponentFixture<SupportedForms>;
type SupportedFormData = TutorialGroupFreePeriodFormData | TutorialGroupSessionFormData | TutorialGroupsConfigurationFormData | TutorialGroupFormData;

export const generateClickSubmitButton = (component: SupportedForms, fixture: SupportedFixtures, expectedEventFormData?: SupportedFormData) => {
    return (expectSubmitEvent: boolean) => {
        const submitFormSpy = jest.spyOn(component, 'submitForm');
        const submitFormEventSpy = jest.spyOn(component.formSubmitted, 'emit');

        const submitButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return fixture.whenStable().then(() => {
            if (expectSubmitEvent) {
                expect(submitFormSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledWith(expectedEventFormData);
            } else {
                expect(submitFormSpy).not.toHaveBeenCalled();
                expect(submitFormEventSpy).not.toHaveBeenCalled();
            }
        });
    };
};

export const generateTestFormIsInvalidOnMissingRequiredProperty = (
    component: SupportedForms,
    fixture: SupportedFixtures,
    setValidFormValues: () => void,
    clickSubmit: (expectSubmitEvent: boolean) => void,
) => {
    return (controlName: string, subFormName?: string) => {
        setValidFormValues();

        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        if (subFormName) {
            component.form.get(subFormName)!.get(controlName)!.setValue(null);
        } else {
            component.form.get(controlName)!.setValue(undefined);
        }
        fixture.detectChanges();
        expect(component.form.invalid).toBeTrue();
        expect(component.isSubmitPossible).toBeFalse();

        clickSubmit(false);
    };
};
