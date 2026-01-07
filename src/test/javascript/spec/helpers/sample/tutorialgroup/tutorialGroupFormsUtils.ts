// util methods for testing tutorial group forms as they all follow the same patterns
import { vi } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import {
    TutorialGroupFreePeriodFormComponent,
    TutorialGroupFreePeriodFormData,
} from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import {
    TutorialGroupSessionFormComponent,
    TutorialGroupSessionFormData,
} from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupFormComponent, TutorialGroupFormData } from 'app/tutorialgroup/manage/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import {
    TutorialGroupsConfigurationFormComponent,
    TutorialGroupsConfigurationFormData,
} from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { runOnPushChangeDetection } from '../../on-push-change-detection.helper';
import { By } from '@angular/platform-browser';

type SupportedForms = TutorialGroupFreePeriodFormComponent | TutorialGroupSessionFormComponent | TutorialGroupsConfigurationFormComponent | TutorialGroupFormComponent;
type SupportedFixtures = ComponentFixture<SupportedForms>;
type SupportedFormData = TutorialGroupFreePeriodFormData | TutorialGroupSessionFormData | TutorialGroupsConfigurationFormData | TutorialGroupFormData;

export const generateClickSubmitButton = (component: SupportedForms, fixture: SupportedFixtures, expectedEventFormData?: SupportedFormData) => {
    return (expectSubmitEvent: boolean) => {
        const submitFormSpy = vi.spyOn(component, 'submitForm');
        const submitFormEventSpy = vi.spyOn(component.formSubmitted, 'emit');

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

        runOnPushChangeDetection(fixture);
        expect(component.form.valid).toBe(true);
        expect(component.isSubmitPossible).toBe(true);

        if (subFormName) {
            component.form.get(subFormName)!.get(controlName)!.setValue(null);
        } else {
            component.form.get(controlName)!.setValue(undefined);
        }
        runOnPushChangeDetection(fixture);
        expect(component.form.invalid).toBe(true);
        expect(component.isSubmitPossible).toBe(false);

        clickSubmit(false);
    };
};

export function expectComponentRendered<T>(fixture: ComponentFixture<any>, selector: string): T {
    expect(fixture.nativeElement.innerHTML).toContain(selector);
    const debugElement = fixture.debugElement.query(By.css(selector));
    expect(debugElement).not.toBeNull();
    return debugElement.componentInstance as T;
}
