import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';

describe('TutorialGroupsConfigurationFormComponent', () => {
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
        // The channel section, and its warning, only exist when the course has messaging enabled.
        fixture.componentRef.setInput('course', {
            id: 1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        } as Course);
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

    it('should render the period error message only once the invalid period is touched/dirty', () => {
        const feedback = () => fixture.nativeElement.querySelectorAll('[data-testid="period-error"]');

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

    describe('period pickers', () => {
        const field = (id: string): HTMLInputElement => fixture.nativeElement.querySelector(`#${id}`);
        const periodErrors = () => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="period-error"]')) as HTMLElement[];

        const type = (input: HTMLInputElement, text: string) => {
            input.value = text;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        };

        const blur = (input: HTMLInputElement) => {
            input.dispatchEvent(new Event('blur'));
            fixture.detectChanges();
        };

        it('should render a start and an end picker instead of one range field', () => {
            expect(field('period-start')).not.toBeNull();
            expect(field('period-end')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('#period')).toBeNull();
        });

        it('should write the typed dates to the period control as a start and end pair', () => {
            type(field('period-start'), '01.02.2021');
            expect(component.form.get('period')!.value).toBeUndefined();

            type(field('period-end'), '01.03.2021');

            const period = component.form.get('period')!.value as Date[];
            expect(period).toHaveLength(2);
            expect(dayjs(period[0]).format('YYYY-MM-DD')).toBe('2021-02-01');
            expect(dayjs(period[1]).format('YYYY-MM-DD')).toBe('2021-03-01');
            expect(component.form.get('period')!.valid).toBe(true);
        });

        it('should accept a period that starts and ends on the same day', () => {
            type(field('period-start'), '01.02.2021');
            type(field('period-end'), '01.02.2021');

            expect(component.form.get('period')!.errors).toBeNull();
        });

        it('should show the period held by the control in both pickers', () => {
            component.form.get('period')!.setValue([new Date(2021, 1, 1), new Date(2021, 2, 1)]);
            fixture.detectChanges();

            expect(field('period-start').value).toBe('01.02.2021');
            expect(field('period-end').value).toBe('01.03.2021');

            component.form.get('period')!.setValue(undefined);
            fixture.detectChanges();

            expect(field('period-start').value).toBe('');
            expect(field('period-end').value).toBe('');
        });

        it('should treat an incomplete period as missing and explain it once touched', () => {
            type(field('period-start'), '01.02.2021');
            blur(field('period-start'));

            expect(component.form.get('period')!.errors).toEqual({ required: true });
            expect(component.isSubmitPossible).toBe(false);
            expect(periodErrors()).toHaveLength(1);
            expect(periodErrors()[0].id).toBe('period-required-error');
            expect(field('period-end').getAttribute('aria-invalid')).toBe('true');
            expect(field('period-start').getAttribute('aria-invalid')).toBe('false');
            expect(field('period-end').getAttribute('aria-describedby')).toBe('period-required-error');
        });

        it('should reject a start after the end, explain it and mark both pickers invalid', () => {
            type(field('period-start'), '01.03.2021');
            type(field('period-end'), '01.02.2021');
            blur(field('period-end'));

            expect(component.form.get('period')!.errors).toEqual({ invalidRange: true });
            expect(component.isSubmitPossible).toBe(false);
            expect(periodErrors()).toHaveLength(1);
            expect(periodErrors()[0].id).toBe('period-range-error');
            expect(field('period-start').getAttribute('aria-invalid')).toBe('true');
            expect(field('period-end').getAttribute('aria-invalid')).toBe('true');
        });

        it('should treat a period with a typed date that is not a date as missing', () => {
            type(field('period-start'), '01.02.2021');
            type(field('period-end'), '01.03.2021');
            expect(component.form.get('period')!.valid).toBe(true);

            type(field('period-end'), '01.03.');

            expect(component.form.get('period')!.errors).toEqual({ required: true });
            expect(component.isSubmitPossible).toBe(false);
        });

        it('should submit the period as dates', () => {
            const submitSpy = vi.spyOn(component.formSubmitted, 'emit');
            type(field('period-start'), '01.02.2021');
            type(field('period-end'), '01.03.2021');

            component.submitForm();

            const emitted = submitSpy.mock.calls[0][0] as TutorialGroupsConfigurationFormData;
            expect(emitted.period).toHaveLength(2);
            expect(emitted.period![0]).toBeInstanceOf(Date);
            expect(emitted.period![1]).toBeInstanceOf(Date);
        });
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
