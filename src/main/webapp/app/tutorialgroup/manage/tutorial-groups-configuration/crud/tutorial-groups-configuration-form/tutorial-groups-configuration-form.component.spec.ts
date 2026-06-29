import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import {
    TutorialGroupsConfigurationFormComponent,
    TutorialGroupsConfigurationFormData,
    tutorialPeriodRangeValidator,
} from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from 'test/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';
import { runOnPushChangeDetection } from 'test/helpers/on-push-change-detection.helper';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TutorialGroupsConfigurationFormComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupsConfigurationFormComponent>;
    let component: TutorialGroupsConfigurationFormComponent;

    const validPeriodStart = new Date(Date.UTC(2021, 1, 1));
    const validPeriodEnd = new Date(Date.UTC(2021, 2, 1));
    const validPeriod = [validPeriodStart, validPeriodEnd];

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ReactiveFormsModule,
                FormsModule,
                NgbTypeaheadModule,
                TutorialGroupsConfigurationFormComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsConfigurationFormComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('course', { id: 1, postsEnabled: true } as Course);
                clickSubmit = generateClickSubmitButton(component, fixture, {
                    period: validPeriod,
                    usePublicTutorialGroupChannels: true,
                    useTutorialGroupChannels: true,
                });

                testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        (Intl as any).supportedValuesOf = undefined;
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should correctly set form values in edit mode', () => {
        fixture.componentRef.setInput('isEditMode', true);
        runOnPushChangeDetection(fixture);
        const formData: TutorialGroupsConfigurationFormData = {
            period: validPeriod,
            usePublicTutorialGroupChannels: true,
            useTutorialGroupChannels: true,
        };
        fixture.componentRef.setInput('formData', formData);
        fixture.detectChanges();

        const formControlNames = ['period', 'usePublicTutorialGroupChannels', 'useTutorialGroupChannels'];
        formControlNames.forEach((control) => {
            expect(component.form.get(control)?.value).toEqual(formData[control as keyof TutorialGroupsConfigurationFormData]);
        });
    });

    it('should show channel deletion warning when channel option is disabled in edit mode', () => {
        fixture.componentRef.setInput('isEditMode', true);
        runOnPushChangeDetection(fixture);
        const formData: TutorialGroupsConfigurationFormData = {
            period: validPeriod,
            usePublicTutorialGroupChannels: true,
            useTutorialGroupChannels: true,
        };
        fixture.componentRef.setInput('formData', formData);
        fixture.detectChanges();

        component.form.get('useTutorialGroupChannels')!.setValue(false);
        runOnPushChangeDetection(fixture);
        fixture.detectChanges();
        expect(component.showChannelDeletionWarning).toBe(true);
        const channelDeletionWarning = fixture.nativeElement.querySelector('#channelDeletionWarning');
        expect(channelDeletionWarning).not.toBeNull();
    });

    it('should submit valid form', async () => {
        setValidFormValues();
        await runOnPushChangeDetection(fixture);
        fixture.detectChanges();
        expect(component.form.valid).toBe(true);
        expect(component.isSubmitPossible).toBe(true);

        clickSubmit(true);
    });

    it('should block submit when required property is missing', () => {
        const requiredControlNames = ['period'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    });

    it('should render a PrimeNG-style invalid-feedback message only once the invalid period is touched/dirty', () => {
        const feedback = () => fixture.nativeElement.querySelectorAll('.invalid-feedback');

        // invalid (empty period) but still pristine/untouched -> no message yet
        fixture.detectChanges();
        expect(component.isPeriodInvalid).toBe(false);
        expect(feedback()).toHaveLength(0);

        // touched empty period -> the "required" message renders
        component.markPeriodAsTouched();
        fixture.detectChanges();
        expect(component.isPeriodInvalid).toBe(true);
        expect(feedback()).toHaveLength(1);

        // inverted range -> the "invalidRange" message renders (still exactly one message)
        component.form.get('period')!.setValue([validPeriodEnd, validPeriodStart]);
        component.form.get('period')!.markAsDirty();
        fixture.detectChanges();
        expect(component.isPeriodInvalid).toBe(true);
        expect(feedback()).toHaveLength(1);

        // valid range -> no message
        component.form.get('period')!.setValue(validPeriod);
        fixture.detectChanges();
        expect(component.isPeriodInvalid).toBe(false);
        expect(feedback()).toHaveLength(0);
    });

    // === helper functions ===
    const setValidFormValues = () => {
        component.form.get('period')!.setValue(validPeriod);
        component.form.get('usePublicTutorialGroupChannels')!.setValue(true);
        component.form.get('useTutorialGroupChannels')!.setValue(true);
    };
});

describe('tutorialPeriodRangeValidator', () => {
    const start = new Date(Date.UTC(2021, 1, 1));
    const end = new Date(Date.UTC(2021, 2, 1));

    it('should accept a valid, correctly ordered range', () => {
        expect(tutorialPeriodRangeValidator(new FormControl([start, end]))).toBeNull();
    });

    it('should require a range when empty, partial, or non-date', () => {
        expect(tutorialPeriodRangeValidator(new FormControl(undefined))).toEqual({ required: true });
        expect(tutorialPeriodRangeValidator(new FormControl([start]))).toEqual({ required: true });
        expect(tutorialPeriodRangeValidator(new FormControl([start, null]))).toEqual({ required: true });
        expect(tutorialPeriodRangeValidator(new FormControl([start, new Date('invalid')]))).toEqual({ required: true });
    });

    it('should flag an inverted range (start after end)', () => {
        expect(tutorialPeriodRangeValidator(new FormControl([end, start]))).toEqual({ invalidRange: true });
    });
});
